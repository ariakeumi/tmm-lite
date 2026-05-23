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
package org.tinymediamanager.scraper.imdb;

import static org.tinymediamanager.scraper.imdb.ImdbParser.decode;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.Globals;
import org.tinymediamanager.scraper.exceptions.HttpException;
import org.tinymediamanager.scraper.http.BrowserUrl;
import org.tinymediamanager.scraper.http.CookieFileParser;
import org.tinymediamanager.scraper.http.InMemoryCachedUrl;
import org.tinymediamanager.scraper.http.Url;
import org.tinymediamanager.scraper.util.UrlUtil;

class ImdbWorker implements Callable<Document> {
  private static final Logger LOGGER    = LoggerFactory.getLogger(ImdbWorker.class);
  private static String       cookie    = "";
  private static String       userAgent = "";

  protected final String      pageUrl;
  protected final String      language;
  protected final String      country;
  protected final boolean     useBrowserFallback;

  ImdbWorker(String url, String language, String country, boolean useBrowserFallback) {
    this.pageUrl = url;
    this.language = language;
    this.country = country;
    this.useBrowserFallback = useBrowserFallback;
  }

  @Override
  public Document call() throws Exception {
    Document doc = null;

    try {
      Url url;

      // inject language into the url for correct caching
      String urlWithHeader = this.pageUrl + "|Accept-Language=" + getAcceptLanguage(language, country);
      url = new InMemoryCachedUrl(urlWithHeader);

      Path cookieFile = Paths.get(Globals.DATA_FOLDER, "imdb-cookies.txt");
      if (StringUtils.isBlank(cookie) && Files.exists(cookieFile)) {
        String token = CookieFileParser.parseCookieValue(cookieFile, decode("YXdzLXdhZi10b2tlbg==")).orElse("");
        if (StringUtils.isNotBlank(token)) {
          cookie = decode("YXdzLXdhZi10b2tlbg==") + "=" + token;
        }
      }

      if (StringUtils.isNotBlank(cookie)) {
        url.addHeader("Cookie", cookie);
      }

      if (StringUtils.isNotBlank(userAgent)) {
        url.setUserAgent(userAgent);
      }

      doc = fetchDocument(url);
    }
    catch (InterruptedException | InterruptedIOException e) {
      // do not swallow these Exceptions
      Thread.currentThread().interrupt();
      throw e;
    }
    catch (HttpException e) {
      if (e.getStatusCode() == 202 && useBrowserFallback) {
        try {
          // re-try with a browser based engine
          BrowserUrl browserUrl = new BrowserUrl(this.pageUrl);
          doc = fetchDocument(browserUrl);

          // worked - remember cookie
          String extractedCookie = browserUrl.getHeader("Cookie");
          if (StringUtils.isNotBlank(extractedCookie)) {
            cookie = extractedCookie;
          }

          // also remember the user agent used
          if (StringUtils.isNotBlank(browserUrl.getUsedUserAgent())) {
            userAgent = browserUrl.getUsedUserAgent();
          }
        }
        catch (Exception ex) {
          LOGGER.debug("tried to fetch imdb page '{}' with browser engine - '{}'", this.pageUrl, ex.getMessage());
          throw ex;
        }
      }
      else {
        throw e;
      }
    }
    catch (Exception e) {
      LOGGER.debug("tried to fetch imdb page {} - {}", this.pageUrl, e.getMessage());
      throw e;
    }

    return doc;
  }

  private Document fetchDocument(Url url) throws Exception {
    try (InputStream is = url.getInputStream()) {
      if (url.getStatusCode() == 202) {
        // 202 indicates that the bot protection is active
        throw new HttpException(202, "Request blocked by IMDb");
      }
      return Jsoup.parse(is, "UTF-8", "");
    }
  }

  /**
   * generates the accept-language http header for imdb
   *
   * @param language
   *          the language code to be used
   * @param country
   *          the country to be used
   * @return the Accept-Language string
   */
  protected static String getAcceptLanguage(String language, String country) {
    List<String> languageString = new ArrayList<>();

    // first: take the preferred language from settings,
    // but validate whether it is legal or not
    if (StringUtils.isNotBlank(language) && StringUtils.isNotBlank(country) && LocaleUtils.isAvailableLocale(new Locale(language, country))) {
      String combined = language + "-" + country;
      languageString.add(combined);
    }

    // also build langu & default country
    Locale localeFromLanguage = UrlUtil.getLocaleFromLanguage(language);
    String cFromLang = localeFromLanguage.getCountry();
    if (localeFromLanguage != null) {
      String combined = language + (cFromLang.isEmpty() ? "" : "-" + cFromLang);
      if (!languageString.contains(combined)) {
        languageString.add(combined);
      }
    }

    if (StringUtils.isNotBlank(language)) {
      languageString.add(language);
    }

    // second: the JRE language
    Locale jreLocale = Locale.getDefault();
    String jreCountry = jreLocale.getCountry();
    String combined = jreLocale.getLanguage() + (jreCountry.isEmpty() ? "" : "-" + jreCountry);
    if (!languageString.contains(combined)) {
      languageString.add(combined);
    }
    if (!languageString.contains(jreLocale.getLanguage())) {
      languageString.add(jreLocale.getLanguage());
    }

    // third: fallback to en
    if (!languageString.contains("en-US")) {
      languageString.add("en-US");
    }
    if (!languageString.contains("en")) {
      languageString.add("en");
    }

    // build a http header for the preferred language
    StringBuilder languages = new StringBuilder();
    float qualifier = 1f;

    for (String line : languageString) {
      if (languages.length() > 0) {
        languages.append(",");
      }
      languages.append(line);
      if (qualifier < 1) {
        languages.append(String.format(Locale.US, ";q=%1.1f", qualifier));
      }
      qualifier -= 0.1;
    }

    return languages.toString();
  }
}
