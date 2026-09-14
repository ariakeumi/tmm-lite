package org.tinymediamanager.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * Tests for the {@link DatasourceFolderGuard} - done purely on strings so that all OS rule sets can be tested on any platform.
 */
public class DatasourceFolderGuardTest {

  private static final String       WINDOWS_HOME     = "C:\\Users\\tmmuser";
  private static final List<String> WINDOWS_SYSTEM   = Arrays.asList("C:\\Windows", "C:\\Program Files", "C:\\Program Files (x86)", "C:\\ProgramData",
      "C:\\Users\\tmmuser\\AppData\\Roaming", "C:\\Users\\tmmuser\\AppData\\Local");
  private static final List<String> WINDOWS_TMM_DATA = Arrays.asList("D:\\tmm-data");

  private static final String       LINUX_HOME       = "/home/tmmuser";
  private static final List<String> LINUX_SYSTEM     = List.of();
  private static final List<String> LINUX_TMM_DATA   = Arrays.asList("/data");

  private static final String       MAC_HOME         = "/Users/tmmuser";
  private static final List<String> MAC_SYSTEM       = Arrays.asList("/Users/tmmuser/Library");
  private static final List<String> MAC_TMM_DATA     = Arrays.asList("/data");

  @Test
  public void testWindowsDangerousFolders() {
    assertDangerous("C:\\");
    assertDangerous("C:\\Windows");
    assertDangerous("c:\\windows\\system32");
    assertDangerous("C:\\Program Files");
    assertDangerous("C:\\Program Files (x86)\\Common Files");
    assertDangerous("C:\\ProgramData");
    assertDangerous("C:\\Users");
    assertDangerous("C:\\Users\\tmmuser");
    assertDangerous("C:\\Users\\tmmuser\\AppData\\Roaming\\tinyMediaManager");
    assertDangerous("C:\\Users\\tmmuser\\AppData");
    assertDangerous("D:\\tmm-data");
    assertDangerous("D:\\");
  }

  @Test
  public void testWindowsRedirectedUserProfile() {
    // the user profile is redirected to another drive than the system drive - both drives must be blocked, others not
    List<String> systemFolders = List.of("C:\\Windows", "C:\\Program Files");
    String redirectedHome = "D:\\Users\\tmmuser";
    assertThat(DatasourceFolderGuard.check("C:\\", DatasourceFolderGuard.Os.WINDOWS, redirectedHome, systemFolders, List.of())).isPresent();
    assertThat(DatasourceFolderGuard.check("D:\\", DatasourceFolderGuard.Os.WINDOWS, redirectedHome, systemFolders, List.of())).isPresent();
    assertThat(DatasourceFolderGuard.check("D:\\Users\\tmmuser", DatasourceFolderGuard.Os.WINDOWS, redirectedHome, systemFolders, List.of()))
        .isPresent();
    assertThat(DatasourceFolderGuard.check("E:\\", DatasourceFolderGuard.Os.WINDOWS, redirectedHome, systemFolders, List.of())).isEmpty();
    assertThat(DatasourceFolderGuard.check("E:\\media", DatasourceFolderGuard.Os.WINDOWS, redirectedHome, systemFolders, List.of())).isEmpty();
  }

  @Test
  public void testWindowsOkFolders() {
    assertOk("H:\\");
    assertOk("Z:\\media");
    assertOk("C:\\Media");
    assertOk("C:\\Users\\tmmuser\\Videos");
    assertOk("D:\\TV Shows");
    assertOk("D:\\tmm-data\\movies");
    assertOk("\\\\nas\\share\\media");
  }

  @Test
  public void testLinuxDangerousFolders() {
    assertDangerous("/");
    assertDangerous("/dev");
    assertDangerous("/var");
    assertDangerous("/usr");
    assertDangerous("/etc");
    assertDangerous("/mnt");
    assertDangerous("/media");
    assertDangerous("/home");
    assertDangerous("/root");
    assertDangerous("/home/tmmuser");
    assertDangerous("/home/tmmuser/.local");
    assertDangerous("/home/tmmuser/.local/share");
    assertDangerous("/home/tmmuser/.config");
    assertDangerous("/data");
  }

  @Test
  public void testLinuxOkFolders() {
    assertOk("/mnt/nas/media");
    assertOk("/media/usb/TV");
    assertOk("/home/tmmuser/Videos");
    assertOk("/data/movies");
    assertOk("/srv/dev-disk-by-label-media/shows");
  }

  @Test
  public void testMacOsDangerousFolders() {
    assertDangerous("/");
    assertDangerous("/Volumes");
    assertDangerous("/Library");
    assertDangerous("/Applications");
    assertDangerous("/System");
    assertDangerous("/Users");
    assertDangerous("/Users/tmmuser");
    assertDangerous("/Users/tmmuser/Library");
    assertDangerous("/Users/tmmuser/Library/Application Support");
    assertDangerous("/Users/tmmuser/.Trash");
  }

  @Test
  public void testMacOsOkFolders() {
    assertOk("/Volumes/MYNAS/movies");
    assertOk("/Users/tmmuser/Movies");
  }

  @Test
  public void testBlankPath() {
    assertThat(DatasourceFolderGuard.check("", DatasourceFolderGuard.Os.LINUX, LINUX_HOME, LINUX_SYSTEM, LINUX_TMM_DATA)).isPresent();
    assertThat(DatasourceFolderGuard.check(null, DatasourceFolderGuard.Os.LINUX, LINUX_HOME, LINUX_SYSTEM, LINUX_TMM_DATA)).isPresent();
  }

  private static void assertDangerous(String path) {
    assertThat(check(path)).as("expect '" + path + "' to be dangerous").isPresent();
  }

  private static void assertOk(String path) {
    assertThat(check(path)).as("expect '" + path + "' to be ok").isEmpty();
  }

  private static java.util.Optional<String> check(String path) {
    DatasourceFolderGuard.Os os = guessOs(path);
    switch (os) {
      case WINDOWS:
        return DatasourceFolderGuard.check(path, os, WINDOWS_HOME, WINDOWS_SYSTEM, WINDOWS_TMM_DATA);

      case MAC_OS:
        return DatasourceFolderGuard.check(path, os, MAC_HOME, MAC_SYSTEM, MAC_TMM_DATA);

      default:
        return DatasourceFolderGuard.check(path, os, LINUX_HOME, LINUX_SYSTEM, LINUX_TMM_DATA);
    }
  }

  private static DatasourceFolderGuard.Os guessOs(String path) {
    if (path.startsWith("\\\\") || path.matches("^[A-Za-z]:.*")) {
      return DatasourceFolderGuard.Os.WINDOWS;
    }
    if (path.startsWith("/Users") || path.equals("/Volumes") || path.equals("/Library") || path.equals("/Applications") || path.equals("/System")) {
      return DatasourceFolderGuard.Os.MAC_OS;
    }
    return DatasourceFolderGuard.Os.LINUX;
  }
}
