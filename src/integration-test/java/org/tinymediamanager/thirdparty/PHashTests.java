package org.tinymediamanager.thirdparty;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.Test;
import org.tinymediamanager.scraper.util.VideoPHash;

public class PHashTests {

  @Test
  public void getPHash() throws IOException, InterruptedException {
    String hash = VideoPHash.generate(Path.of("some/file/e78f8d1ca6b208e9.mp4"));
    System.out.println(hash);
  }
}