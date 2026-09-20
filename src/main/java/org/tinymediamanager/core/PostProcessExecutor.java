/*
 * Copyright 2012 - 2026 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.Globals;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.threading.TmmTask;

/**
 * the class {@link PostProcessExecutor} executes post process steps for movies
 *
 * @author Manuel Laggner, Wolfgang Janess, Myron Boyle
 */
public abstract class PostProcessExecutor extends TmmTask {
  private static final Logger                      LOGGER = LoggerFactory.getLogger(PostProcessExecutor.class);

  protected final PostProcess                      postProcess;
  protected final List<? extends MediaEntity>      entities;
  protected final List<String>                     processOutput;
  protected final List<PostProcessExecutionResult> executionResults;

  protected PostProcessExecutor(PostProcess postProcess, List<? extends MediaEntity> entities) {
    super(postProcess.getName(), entities.size(), TaskType.BACKGROUND_TASK);
    this.postProcess = postProcess;
    this.entities = new ArrayList<>(entities);
    this.processOutput = new ArrayList<>();
    this.executionResults = new ArrayList<>();
  }

  @Override
  protected void doInBackground() {
    execute();
  }

  /**
   * prepare the command end execute it with {@link PostProcessExecutor#executeCommand} in the end
   */
  protected abstract void execute();

  /**
   * Gets all collected execution results.
   *
   * @return an unmodifiable list of execution results
   */
  public List<PostProcessExecutionResult> getExecutionResults() {
    return Collections.unmodifiableList(executionResults);
  }

  /**
   * Handles a failed execution for one entity. When abort-on-failure is enabled in the post-process configuration, the task is marked as failed and
   * the caller should stop processing the remaining entities.
   *
   * @param mediaEntity
   *          the entity for which the execution failed
   * @param e
   *          the exception thrown while executing the command
   * @return true when the execution queue should be aborted
   */
  protected boolean handleExecutionFailure(MediaEntity mediaEntity, Exception e) {
    if (e instanceof InterruptedException || !postProcess.isAbortOnFailure()) {
      return false;
    }

    LOGGER.error("Aborting post process '{}' - execution for '{}' failed", postProcess.getName(), mediaEntity.getTitle());
    setState(TaskState.FAILED);
    return true;
  }

  /**
   * execute the given command and return the response
   *
   * @param cmdline
   *          the command to execute
   * @param mediaEntity
   *          the media entity for which the command is executed - used to set the working dir
   * @throws IOException
   *           if an I/O error occurs
   * @throws InterruptedException
   *           if the process is interrupted
   */
  protected void executeCommand(String[] cmdline, MediaEntity mediaEntity) throws IOException, InterruptedException {
    List<String> commandList = new ArrayList<>();
    ProcessBuilder pb;
    String p = "";
    if (postProcess.getPath() != null) {
      p = postProcess.getPath().toLowerCase(Locale.ROOT);
    }
    if (SystemUtils.IS_OS_WINDOWS) {
      commandList.add("powershell");

      // path filled with some program
      if (p.endsWith("exe") || p.endsWith("com")) {
        commandList.add("&");
        commandList.add("'" + postProcess.getPath() + "'"); // needs to be quoted
        for (String cmd : cmdline) {
          commandList.add("\\\"" + cmd + "\\\""); // add all params with double quote around
        }
      }
      // powershell scripting file
      else if (p.endsWith("ps1")) {
        commandList.add("-ExecutionPolicy"); // default security restriction bypass
        commandList.add("ByPass");
        commandList.add("-File");
        commandList.add(postProcess.getPath()); // needs to be UNquoted
        commandList.addAll(Arrays.asList(cmdline));
      }
      // standard cmd, ONLY if we not operating on a network share // TODO: find better way
      else if ((p.endsWith("bat") || p.endsWith("cmd")) && !mediaEntity.getDataSource().startsWith("\\\\")) {
        commandList.add("&");
        commandList.add("'" + postProcess.getPath() + "'");
        for (String cmd : cmdline) {
          commandList.add("\\\"" + cmd + "\\\""); // add all params with double quote around
        }
      }
      else {
        // just commands - CONVERT TO SINGLE STRING - be sure to use delimiter ";" if multiple commands!!!
        commandList.add("-Command");
        commandList.add(String.join(" ", cmdline));
      }
      pb = new ProcessBuilder(commandList);
    }
    else {
      // Linux & Mac
      pb = new ProcessBuilder("/bin/sh", "-c", postProcess.getPath() + " " + String.join(" ", cmdline));
    }

    pb.redirectErrorStream(true);

    Path entityPath = mediaEntity.getPathNIO();
    if (entityPath != null) {
      // set the workdir to the entity folder
      pb.directory(entityPath.toFile());
    }
    else {
      // fallback: set to the content folder (to avoid writing to the signed tmm folder)
      pb.directory(Paths.get(Globals.CONTENT_FOLDER).toFile());
    }

    LOGGER.debug("Running command: {}", pb.command());
    final Process process = pb.start();

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      Thread outputReaderThread = new Thread(() -> {
        try {
          IOUtils.copy(process.getInputStream(), outputStream);
        }
        catch (IOException e) {
          LOGGER.debug("could not get output from the process", e);
        }
      });
      outputReaderThread.start();

      int processValue = process.waitFor();
      outputReaderThread.join();

      String response = outputStream.toString(StandardCharsets.UTF_8);
      executionResults.add(new PostProcessExecutionResult(postProcess, mediaEntity.getTitle(), pb.command(), response));
      if (processValue != 0) {
        LOGGER.error("Error running post-process script - '{}'", response);
        throw new IOException("error running script - code '" + processValue + "'");
      }
      if (StringUtils.isNotBlank(response)) {
        processOutput.add(response);
        LOGGER.debug(response);
      }
      LOGGER.trace("PostProcessing: END");
    }
    finally {
      process.destroy();
      // Process must be destroyed before closing streams, can't use try-with-resources,
      // as resources are closing when leaving try block, before finally
      IOUtils.close(process.getErrorStream());
    }
  }
}
