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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Regression test for work item #3344: {@link java.text.SimpleDateFormat} is not thread-safe and must not be shared across threads.
 */
public class TmmDateFormatConcurrencyTest extends BasicTest {

  private static final int THREADS    = 16;
  private static final int ITERATIONS = 2000;

  @Test
  public void testConcurrentFormatAndParse() throws Exception {
    final Date fixedDate = new Date(1609459200000L); // 2021-01-01T00:00:00Z

    final String expectedDate = TmmDateFormat.getDateFormat().format(fixedDate);
    final String expectedShortTime = TmmDateFormat.getDateShortTimeFormat().format(fixedDate);
    final String expectedMediumTime = TmmDateFormat.getDateMediumTimeFormat().format(fixedDate);

    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    List<Future<Void>> futures = new ArrayList<>();

    for (int t = 0; t < THREADS; t++) {
      futures.add(executor.submit((Callable<Void>) () -> {
        for (int i = 0; i < ITERATIONS; i++) {
          DateFormat dateFormat = TmmDateFormat.getDateFormat();
          assertThat(dateFormat.format(fixedDate)).isEqualTo(expectedDate);
          assertThat(dateFormat.parse(expectedDate)).isNotNull();

          DateFormat shortTimeFormat = TmmDateFormat.getDateShortTimeFormat();
          assertThat(shortTimeFormat.format(fixedDate)).isEqualTo(expectedShortTime);

          DateFormat mediumTimeFormat = TmmDateFormat.getDateMediumTimeFormat();
          assertThat(mediumTimeFormat.format(fixedDate)).isEqualTo(expectedMediumTime);
        }
        return null;
      }));
    }

    for (Future<Void> future : futures) {
      future.get(60, TimeUnit.SECONDS);
    }

    executor.shutdown();
    assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
  }
}
