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

/**
 * The class {@link PostProcess} - to represent a Postprocess
 *
 * @author Wolfgang Janes
 */
public class PostProcess extends AbstractModelObject {
  private String  name;
  private String  path;
  private String  command;
  private boolean showOutput;
  private boolean abortOnFailure;

  public String getName() {
    return name;
  }

  public void setName(String newValue) {
    String oldValue = this.name;
    this.name = newValue;
    firePropertyChange("name", oldValue, newValue);
  }

  public String getPath() {
    return path;
  }

  public void setPath(String newValue) {
    String oldValue = this.path;
    this.path = newValue;
    firePropertyChange("path", oldValue, newValue);
  }

  public String getCommand() {
    return command;
  }

  public void setCommand(String newValue) {
    String oldValue = this.command;
    this.command = newValue;
    firePropertyChange("command", oldValue, newValue);
  }

  public boolean isShowOutput() {
    return showOutput;
  }

  public void setShowOutput(boolean newValue) {
    boolean oldValue = this.showOutput;
    this.showOutput = newValue;
    firePropertyChange("showOutput", oldValue, newValue);
  }

  /**
   * Gets whether the whole entity queue should be aborted when the execution for one entity fails.
   *
   * @return true to abort the queue on failure
   */
  public boolean isAbortOnFailure() {
    return abortOnFailure;
  }

  /**
   * Sets whether the whole entity queue should be aborted when the execution for one entity fails.
   *
   * @param newValue
   *          true to abort the queue on failure
   */
  public void setAbortOnFailure(boolean newValue) {
    boolean oldValue = this.abortOnFailure;
    this.abortOnFailure = newValue;
    firePropertyChange("abortOnFailure", oldValue, newValue);
  }

  @Override
  public String toString() {
    return "PostProcess [path=" + path + ", command=" + command + "]";
  }
}
