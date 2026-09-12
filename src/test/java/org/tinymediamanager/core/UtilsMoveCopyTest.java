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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileExistsException;
import org.junit.Test;

/**
 * Pins down the exact semantics of {@link Utils#moveFileSafe}, {@link Utils#copyFileSafe}, {@link Utils#deleteFileWithBackup} and
 * {@link Utils#listFilesRecursive} BEFORE any performance refactoring.
 */
public class UtilsMoveCopyTest extends BasicTest {

  private Path writeFile(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    return file;
  }

  private String read(Path file) throws IOException {
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }

  // =========================================================================
  // moveFileSafe
  // =========================================================================

  @Test(expected = NullPointerException.class)
  public void moveNullSource() throws IOException {
    Utils.moveFileSafe(null, tmpFolder.getRoot().toPath().resolve("dest"));
  }

  @Test(expected = NullPointerException.class)
  public void moveNullDestination() throws IOException {
    Utils.moveFileSafe(writeFile(tmpFolder.getRoot().toPath().resolve("src.txt"), "x"), null);
  }

  @Test
  public void moveMissingSourceThrowsFileNotFound() throws IOException {
    Path src = tmpFolder.getRoot().toPath().resolve("nope.txt");
    Path dest = tmpFolder.getRoot().toPath().resolve("dest.txt");
    try {
      Utils.moveFileSafe(src, dest);
      throw new AssertionError("expected FileNotFoundException");
    }
    catch (FileNotFoundException e) {
      assertThat(e.getMessage()).contains("does not exist");
    }
  }

  @Test
  public void moveSourceDirectoryThrows() throws IOException {
    Path src = Files.createDirectories(tmpFolder.getRoot().toPath().resolve("srcDir"));
    Path dest = tmpFolder.getRoot().toPath().resolve("dest.txt");
    try {
      Utils.moveFileSafe(src, dest);
      throw new AssertionError("expected IOException");
    }
    catch (IOException e) {
      assertThat(e.getMessage()).contains("is a directory");
    }
  }

  @Test
  public void moveExistingDestinationThrowsFileExists() throws IOException {
    Path src = writeFile(tmpFolder.getRoot().toPath().resolve("src.txt"), "source");
    Path dest = writeFile(tmpFolder.getRoot().toPath().resolve("dest.txt"), "destination");
    try {
      Utils.moveFileSafe(src, dest);
      throw new AssertionError("expected FileExistsException");
    }
    catch (FileExistsException e) {
      assertThat(e.getMessage()).contains("already exists");
    }
    assertThat(read(src)).isEqualTo("source");
    assertThat(read(dest)).isEqualTo("destination");
  }

  @Test
  public void moveDestinationDirectoryThrows() throws IOException {
    Path src = writeFile(tmpFolder.getRoot().toPath().resolve("src.txt"), "source");
    Path dest = Files.createDirectories(tmpFolder.getRoot().toPath().resolve("destDir"));
    try {
      Utils.moveFileSafe(src, dest);
      throw new AssertionError("expected FileExistsException");
    }
    catch (FileExistsException e) {
      // an existing directory destination is reported as "already exists" (check precedes the isDirectory one)
      assertThat(e.getMessage()).contains("already exists");
    }
    assertThat(Files.exists(src)).isTrue();
  }

  @Test
  public void moveEqualPathsIsNoop() throws IOException {
    Path src = writeFile(tmpFolder.getRoot().toPath().resolve("same.txt"), "content");
    assertThat(Utils.moveFileSafe(src, src)).isTrue();
    assertThat(read(src)).isEqualTo("content");
  }

  @Test
  public void moveRegularFile() throws IOException {
    Path src = writeFile(tmpFolder.getRoot().toPath().resolve("moveA.txt"), "moved content");
    Path dest = tmpFolder.getRoot().toPath().resolve("moveSub").resolve("moveB.mkv");
    Files.createDirectories(dest.getParent());
    assertThat(Utils.moveFileSafe(src, dest)).isTrue();
    assertThat(Files.exists(src)).isFalse();
    assertThat(read(dest)).isEqualTo("moved content");
  }

  @Test
  public void moveBrokenSymlink() throws IOException {
    // allow moving of symlinks even when the target is missing (issue #410)
    Path src = tmpFolder.getRoot().toPath().resolve("linkA.nfo");
    Path dest = tmpFolder.getRoot().toPath().resolve("linkB.nfo");
    try {
      Files.createSymbolicLink(src, tmpFolder.getRoot().toPath().resolve("doesNotExistAnywhere.nfo"));
    }
    catch (IOException | UnsupportedOperationException | SecurityException e) {
      assumeTrue("symlinks not supported here", false);
    }
    assertThat(Utils.moveFileSafe(src, dest)).isTrue();
    assertThat(Files.exists(dest, LinkOption.NOFOLLOW_LINKS)).isTrue();
  }

  @Test
  public void moveCaseOnlyRename() throws IOException {
    // on case-insensitive filesystems the dest "exists" and IS the same file -> must rename, not throw
    Path lower = writeFile(tmpFolder.getRoot().toPath().resolve("caseTest.txt"), "content");
    Path upper = lower.resolveSibling("caseTest.TXT");
    assumeTrue("case-insensitive filesystem needed", Files.exists(upper) && Files.isSameFile(lower, upper));
    assertThat(Utils.moveFileSafe(lower, upper)).isTrue();
    assertThat(read(upper)).isEqualTo("content");
  }

  // =========================================================================
  // copyFileSafe
  // =========================================================================

  @Test(expected = NullPointerException.class)
  public void copyNullSource() throws IOException {
    Utils.copyFileSafe(null, tmpFolder.getRoot().toPath().resolve("dest"));
  }

  @Test(expected = NullPointerException.class)
  public void copyNullDestination() throws IOException {
    Utils.copyFileSafe(writeFile(tmpFolder.getRoot().toPath().resolve("src.txt"), "x"), null);
  }

  @Test
  public void copyMissingSourceThrowsFileNotFound() throws IOException {
    Path src = tmpFolder.getRoot().toPath().resolve("nope.txt");
    try {
      Utils.copyFileSafe(src, tmpFolder.getRoot().toPath().resolve("dest.txt"));
      throw new AssertionError("expected FileNotFoundException");
    }
    catch (FileNotFoundException e) {
      assertThat(e.getMessage()).contains("does not exist");
    }
  }

  @Test
  public void copySourceDirectoryThrows() throws IOException {
    Path src = Files.createDirectories(tmpFolder.getRoot().toPath().resolve("copySrcDir"));
    try {
      Utils.copyFileSafe(src, tmpFolder.getRoot().toPath().resolve("dest.txt"));
      throw new AssertionError("expected IOException");
    }
    catch (IOException e) {
      assertThat(e.getMessage()).contains("is a directory");
    }
  }

  @Test
  public void copyExistingDestinationNoOverwriteThrows() throws IOException {
    Path src = writeFile(tmpFolder.getRoot().toPath().resolve("src.txt"), "source");
    Path dest = writeFile(tmpFolder.getRoot().toPath().resolve("dest.txt"), "destination");
    try {
      Utils.copyFileSafe(src, dest, false);
      throw new AssertionError("expected FileExistsException");
    }
    catch (FileExistsException e) {
      assertThat(e.getMessage()).contains("already exists");
    }
    assertThat(read(dest)).isEqualTo("destination");
  }

  @Test
  public void copyWithOverwrite() throws IOException {
    Path src = writeFile(tmpFolder.getRoot().toPath().resolve("src.txt"), "source");
    Path dest = writeFile(tmpFolder.getRoot().toPath().resolve("dest.txt"), "destination");
    assertThat(Utils.copyFileSafe(src, dest, true)).isTrue();
    assertThat(read(src)).isEqualTo("source");
    assertThat(read(dest)).isEqualTo("source");
  }

  @Test
  public void copyDestinationDirectoryThrows() throws IOException {
    Path src = writeFile(tmpFolder.getRoot().toPath().resolve("src.txt"), "source");
    Path dest = Files.createDirectories(tmpFolder.getRoot().toPath().resolve("copyDestDir"));
    try {
      Utils.copyFileSafe(src, dest, true);
      throw new AssertionError("expected IOException");
    }
    catch (FileExistsException e) {
      throw new AssertionError("expected IOException but got FileExistsException", e);
    }
    catch (IOException e) {
      assertThat(e.getMessage()).contains("is a directory");
    }
  }

  @Test
  public void copyEqualPathsIsNoop() throws IOException {
    Path src = writeFile(tmpFolder.getRoot().toPath().resolve("same.txt"), "content");
    assertThat(Utils.copyFileSafe(src, src)).isTrue();
    assertThat(read(src)).isEqualTo("content");
  }

  @Test
  public void copyRegularFile() throws IOException {
    Path src = writeFile(tmpFolder.getRoot().toPath().resolve("copyA.txt"), "copied content");
    Path dest = tmpFolder.getRoot().toPath().resolve("copySub").resolve("copyB.txt");
    Files.createDirectories(dest.getParent());
    assertThat(Utils.copyFileSafe(src, dest)).isTrue();
    assertThat(read(src)).isEqualTo("copied content");
    assertThat(read(dest)).isEqualTo("copied content");
  }

  // =========================================================================
  // deleteFileWithBackup
  // =========================================================================

  @Test
  public void deleteWithoutTrashRemovesFile() throws IOException {
    Settings.getInstance().setEnableTrash(false);
    Path datasource = tmpFolder.newFolder("dsNoTrash").toPath().toAbsolutePath();
    Path file = writeFile(datasource.resolve("gone.txt"), "x");
    assertThat(Utils.deleteFileWithBackup(file, datasource.toString())).isTrue();
    assertThat(Files.exists(file)).isFalse();
  }

  @Test
  public void deleteWithBackupKeepsRelativePath() throws IOException {
    Settings.getInstance().setEnableTrash(true);
    Path datasource = tmpFolder.newFolder("dsBackup").toPath().toAbsolutePath();
    Path file = writeFile(datasource.resolve("season 1").resolve("episode.nfo"), "nfo");

    assertThat(Utils.deleteFileWithBackup(file, datasource.toString())).isTrue();
    assertThat(Files.exists(file)).isFalse();

    Path backup = datasource.resolve(Constants.DS_TRASH_FOLDER).resolve("season 1").resolve("episode.nfo");
    assertThat(Files.exists(backup)).isTrue();
    assertThat(read(backup)).isEqualTo("nfo");

    // the .nomedia marker should exist in the trash folder
    assertThat(Files.exists(datasource.resolve(Constants.DS_TRASH_FOLDER).resolve(".nomedia"))).isTrue();
  }

  @Test
  public void deleteWithBackupRefusesForeignPath() throws IOException {
    Settings.getInstance().setEnableTrash(true);
    Path datasource = tmpFolder.newFolder("ds1").toPath().toAbsolutePath();
    Path file = writeFile(tmpFolder.newFolder("elsewhere").toPath().resolve("file.nfo"), "x");
    assertThat(Utils.deleteFileWithBackup(file, datasource.toString())).isFalse();
    assertThat(Files.exists(file)).isTrue();
  }

  @Test
  public void deleteWithBackupOnMissingFileSucceeds() throws IOException {
    Settings.getInstance().setEnableTrash(true);
    Path datasource = tmpFolder.newFolder("dsMissing").toPath().toAbsolutePath();
    assertThat(Utils.deleteFileWithBackup(datasource.resolve("neverExisted.txt"), datasource.toString())).isTrue();
  }

  // =========================================================================
  // listFilesRecursive
  // =========================================================================

  @Test
  public void listFilesRecursiveFindsAllFiles() throws IOException {
    Path root = tmpFolder.newFolder("tree").toPath();
    writeFile(root.resolve("a.txt"), "a");
    writeFile(root.resolve("sub1").resolve("b.txt"), "b");
    writeFile(root.resolve("sub1").resolve("sub2").resolve("c.txt"), "c");

    List<Path> files = Utils.listFilesRecursive(root);
    assertThat(files).extracting(p -> p.getFileName().toString()).containsExactlyInAnyOrder("a.txt", "b.txt", "c.txt");
  }

  @Test
  public void listFilesRecursiveOnFileOrMissingReturnsEmpty() throws IOException {
    Path root = tmpFolder.newFolder("tree2").toPath();
    Path file = writeFile(root.resolve("a.txt"), "a");
    assertThat(Utils.listFilesRecursive(file)).isEmpty();
    assertThat(Utils.listFilesRecursive(root.resolve("missing"))).isEmpty();
  }
}
