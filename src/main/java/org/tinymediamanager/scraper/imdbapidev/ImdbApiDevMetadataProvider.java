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
package org.tinymediamanager.scraper.imdbapidev;

import java.util.Calendar;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.tinymediamanager.core.FeatureNotEnabledException;
import org.tinymediamanager.core.MediaAiredStatus;
import org.tinymediamanager.core.entities.MediaGenres;
import org.tinymediamanager.core.entities.MediaRating;
import org.tinymediamanager.core.entities.Person;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.entities.MediaArtwork;
import org.tinymediamanager.scraper.entities.MediaCertification;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.imdbapidev.entities.ImdbApiDevCertificate;
import org.tinymediamanager.scraper.imdbapidev.entities.ImdbApiDevCompanyCredit;
import org.tinymediamanager.scraper.imdbapidev.entities.ImdbApiDevCredit;
import org.tinymediamanager.scraper.imdbapidev.entities.ImdbApiDevListTitleCertificatesResponse;
import org.tinymediamanager.scraper.imdbapidev.entities.ImdbApiDevListTitleCompanyCreditsResponse;
import org.tinymediamanager.scraper.imdbapidev.entities.ImdbApiDevListTitleCreditsResponse;
import org.tinymediamanager.scraper.imdbapidev.entities.ImdbApiDevName;
import org.tinymediamanager.scraper.imdbapidev.entities.ImdbApiDevPrecisionDate;
import org.tinymediamanager.scraper.imdbapidev.entities.ImdbApiDevReleaseDate;
import org.tinymediamanager.scraper.imdbapidev.entities.ImdbApiDevTitle;
import org.tinymediamanager.scraper.imdbapidev.entities.ImdbApiDevTitleReleaseDatesResponse;
import org.tinymediamanager.scraper.interfaces.IMediaProvider;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.StrgUtils;

/**
 * The class {@link ImdbApiDevMetadataProvider} is the abstract base class for all imdbapi.dev scrapers.
 * <p>
 * It holds the shared API controller, initialization logic, and common helper methods for mapping API response data to tinyMediaManager's internal
 * metadata model.
 * </p>
 *
 * @author Manuel Laggner
 */
abstract class ImdbApiDevMetadataProvider implements IMediaProvider {

  /** The scraper provider ID */
  protected static final String   ID = "imdbapi-dev";

  private final MediaProviderInfo providerInfo;

  /** The REST API controller - lazily initialized */
  protected ImdbApiDevController  api;

  /**
   * Constructs the provider and creates the {@link MediaProviderInfo}.
   */
  protected ImdbApiDevMetadataProvider() {
    providerInfo = createMediaProviderInfo();
  }

  /**
   * Get the sub-ID for this provider instance (e.g. "movie" or "tvshow").
   *
   * @return the sub-ID string
   */
  protected abstract String getSubId();

  /**
   * Get the SLF4J {@link Logger} for this provider.
   *
   * @return the logger
   */
  protected abstract Logger getLogger();

  /**
   * Create and configure the {@link MediaProviderInfo} for this provider.
   *
   * @return the configured {@link MediaProviderInfo}
   */
  protected MediaProviderInfo createMediaProviderInfo() {
    MediaProviderInfo info = new MediaProviderInfo(ID, getSubId(), "imdbapi.dev",
        "<html><h3>imdbapi.dev</h3><br />A fast and modern API providing access to movie and TV show data "
            + "including metadata, cast, episodes and certifications.<br /><br />" + "Available languages: EN</html>",
        ImdbApiDevMetadataProvider.class.getResource("/org/tinymediamanager/scraper/imdbapi_dev.svg"));

    info.getConfig().load();

    return info;
  }

  @Override
  public MediaProviderInfo getProviderInfo() {
    return providerInfo;
  }

  @Override
  public boolean isActive() {
    return isFeatureEnabled();
  }

  /**
   * Thread-safe lazy initialization of the API controller.
   *
   * @throws ScrapeException
   *           if the provider is not active or the API cannot be initialized
   */
  protected synchronized void initAPI() throws ScrapeException {
    if (api == null) {
      if (!isActive()) {
        throw new ScrapeException(new FeatureNotEnabledException(this));
      }
      try {
        api = new ImdbApiDevController(getApiKey());
      }
      catch (Exception e) {
        getLogger().error("could not initialize the imdbapi.dev API: {}", e.getMessage());
        api = null;
        throw new ScrapeException(e);
      }
    }
  }

  /**
   * Fills the given {@link MediaMetadata} with data from an {@link ImdbApiDevTitle}.
   * <p>
   * Sets the title, original title, year, runtime, plot, genres, rating, poster artwork, countries of origin, spoken languages and cast/crew from the
   * title summary data.
   * </p>
   *
   * @param md
   *          the {@link MediaMetadata} to fill
   * @param title
   *          the {@link ImdbApiDevTitle} to read data from
   */
  protected void fillMetadataFromTitle(MediaMetadata md, ImdbApiDevTitle title) {
    if (StringUtils.isNotBlank(title.id)) {
      md.setId(MediaMetadata.IMDB, title.id);
    }

    if (StringUtils.isNotBlank(title.primaryTitle)) {
      md.setTitle(title.primaryTitle);
    }

    if (StringUtils.isNotBlank(title.originalTitle)) {
      md.setOriginalTitle(title.originalTitle);
    }
    else {
      md.setOriginalTitle(title.primaryTitle);
    }

    if (title.startYear != null && title.startYear > 0) {
      md.setYear(title.startYear);
    }
    if (title.endYear != null && title.endYear > 0) {
      md.setYearEnd(title.endYear);
      md.setStatus(MediaAiredStatus.ENDED);
    }

    if (title.runtimeSeconds != null && title.runtimeSeconds > 0) {
      // convert seconds to minutes
      md.setRuntime(title.runtimeSeconds / 60);
    }

    if (StringUtils.isNotBlank(title.plot)) {
      md.setPlot(title.plot);
    }

    // genres
    for (String genre : ListUtils.nullSafe(title.genres)) {
      MediaGenres tmmGenre = MediaGenres.getGenre(genre);
      if (tmmGenre != null) {
        md.addGenre(tmmGenre);
      }
    }

    // rating
    if (title.rating != null && title.rating.aggregateRating != null && title.rating.aggregateRating > 0) {
      MediaRating rating = new MediaRating(MediaMetadata.IMDB);
      rating.setRating(title.rating.aggregateRating);
      rating.setMaxValue(10);
      if (title.rating.voteCount != null) {
        rating.setVotes(title.rating.voteCount);
      }
      md.addRating(rating);
    }

    // poster artwork
    if (title.primaryImage != null && StringUtils.isNotBlank(title.primaryImage.url)) {
      MediaArtwork poster = new MediaArtwork(ID, MediaArtwork.MediaArtworkType.POSTER);
      poster.setOriginalUrl(title.primaryImage.url);
      poster.setPreviewUrl(title.primaryImage.url);
      if (title.primaryImage.width != null && title.primaryImage.height != null) {
        poster.addImageSize(title.primaryImage.width, title.primaryImage.height, title.primaryImage.url,
            MediaArtwork.PosterSizes.getSizeOrder(title.primaryImage.width));
      }
      md.addMediaArt(poster);
    }

    // countries of origin
    for (var country : ListUtils.nullSafe(title.originCountries)) {
      if (StringUtils.isNotBlank(country.code)) {
        md.addCountry(country.code);
      }
    }

    // spoken languages
    for (var language : ListUtils.nullSafe(title.spokenLanguages)) {
      if (StringUtils.isNotBlank(language.code)) {
        md.addSpokenLanguage(language.code);
      }
    }

    // directors from the title summary
    for (ImdbApiDevName director : ListUtils.nullSafe(title.directors)) {
      addPersonToMetadata(md, director, Person.Type.DIRECTOR, null);
    }

    // writers from the title summary
    for (ImdbApiDevName writer : ListUtils.nullSafe(title.writers)) {
      addPersonToMetadata(md, writer, Person.Type.WRITER, null);
    }

    // stars from the title summary
    for (ImdbApiDevName star : ListUtils.nullSafe(title.stars)) {
      addPersonToMetadata(md, star, Person.Type.ACTOR, null);
    }
  }

  /**
   * Fetches the full cast and crew for a given title ID and merges them into the metadata.
   * <p>
   * Clears any persons previously added from the title summary and replaces them with the full credits list. Handles pagination to retrieve all
   * available credits.
   * </p>
   *
   * @param md
   *          the {@link MediaMetadata} to merge credits into
   * @param imdbId
   *          the IMDb title ID
   */
  protected void fetchAndMergeCredits(MediaMetadata md, String imdbId) {
    try {
      // clear persons already added from the title summary
      md.getCastMembers().clear();

      String pageToken = null;
      do {
        ImdbApiDevListTitleCreditsResponse response = api.titleService().getCredits(imdbId, 50, pageToken).execute().body();
        if (response == null || ListUtils.isEmpty(response.credits)) {
          break;
        }

        for (ImdbApiDevCredit credit : response.credits) {
          if (credit.name == null || StringUtils.isBlank(credit.name.displayName)) {
            continue;
          }

          Person.Type type = mapCreditCategoryToPersonType(credit.category);
          if (type == null) {
            continue;
          }

          String role = null;
          if ((type == Person.Type.ACTOR || type == Person.Type.GUEST) && ListUtils.isNotEmpty(credit.characters)) {
            role = String.join(", ", credit.characters);
          }
          else if (type != Person.Type.ACTOR && type != Person.Type.GUEST) {
            role = StrgUtils.capitalize(credit.category);
          }

          addPersonToMetadata(md, credit.name, type, role);
        }

        pageToken = response.nextPageToken;
      } while (StringUtils.isNotBlank(pageToken));
    }
    catch (Exception e) {
      getLogger().debug("could not fetch credits for {}: {}", imdbId, e.getMessage());
    }
  }

  /**
   * Fetches content rating certificates for a given title ID and adds the best match to the metadata.
   * <p>
   * Prefers the certificate for the specified country, falling back to the US certificate if not found.
   * </p>
   *
   * @param md
   *          the {@link MediaMetadata} to add the certification to
   * @param imdbId
   *          the IMDb title ID
   * @param countryCode
   *          the preferred ISO 3166-1 alpha-2 country code (e.g. "US", "DE")
   */
  protected void fetchAndMergeCertifications(MediaMetadata md, String imdbId, String countryCode) {
    try {
      ImdbApiDevListTitleCertificatesResponse response = api.titleService().getCertificates(imdbId).execute().body();
      if (response == null || ListUtils.isEmpty(response.certificates)) {
        return;
      }

      // prefer the certificate for the requested country, fall back to US
      ImdbApiDevCertificate preferred = null;
      ImdbApiDevCertificate fallback = null;

      for (ImdbApiDevCertificate cert : response.certificates) {
        if (cert.country == null || StringUtils.isBlank(cert.rating)) {
          continue;
        }
        if (countryCode.equalsIgnoreCase(cert.country.code)) {
          preferred = cert;
          break;
        }
        if (fallback == null && "US".equalsIgnoreCase(cert.country.code)) {
          fallback = cert;
        }
      }

      ImdbApiDevCertificate chosen = preferred != null ? preferred : fallback;
      if (chosen != null && chosen.country != null) {
        String certCountry = StringUtils.isNotBlank(chosen.country.code) ? chosen.country.code : "US";
        MediaCertification cert = MediaCertification.getCertification(certCountry, chosen.rating);
        if (cert != null && cert != MediaCertification.UNKNOWN) {
          md.addCertification(cert);
        }
      }
    }
    catch (Exception e) {
      getLogger().debug("could not fetch certifications for {}: {}", imdbId, e.getMessage());
    }
  }

  /**
   * Fetches release information for a given title ID and sets the best matching release date.
   * <p>
   * Selection order is:
   * </p>
   * <ol>
   * <li>Main release date for the preferred country</li>
   * <li>Main release date for any country</li>
   * <li>Earliest (lowest) available release date</li>
   * </ol>
   *
   * @param md
   *          the {@link MediaMetadata} to set the release date on
   * @param imdbId
   *          the IMDb title ID
   * @param preferredCountryCode
   *          the preferred ISO 3166-1 alpha-2 country code
   */
  protected void fetchAndMergeReleaseDate(MediaMetadata md, String imdbId, String preferredCountryCode) {
    try {
      ImdbApiDevTitleReleaseDatesResponse response = api.titleService().getReleaseDates(imdbId).execute().body();
      if (response == null || ListUtils.isEmpty(response.releaseDates)) {
        return;
      }

      Date preferredMainDate = null;
      Date mainDate = null;
      Date lowestDate = null;

      for (ImdbApiDevReleaseDate releaseInfo : ListUtils.nullSafe(response.releaseDates)) {
        Date releaseDate = toDate(releaseInfo.releaseDate);
        if (releaseDate == null) {
          continue;
        }

        if (lowestDate == null || releaseDate.before(lowestDate)) {
          lowestDate = releaseDate;
        }

        if (!Boolean.TRUE.equals(releaseInfo.isMain)) {
          continue;
        }

        if (mainDate == null) {
          mainDate = releaseDate;
        }

        if (StringUtils.isBlank(preferredCountryCode)) {
          continue;
        }

        String countryCode = getReleaseInfoCountryCode(releaseInfo);
        if (preferredCountryCode.equalsIgnoreCase(countryCode)) {
          if (preferredMainDate == null || releaseDate.before(preferredMainDate)) {
            preferredMainDate = releaseDate;
          }
        }
      }

      Date chosenDate = preferredMainDate != null ? preferredMainDate : (mainDate != null ? mainDate : lowestDate);
      if (chosenDate != null) {
        md.setReleaseDate(chosenDate);
      }
    }
    catch (Exception e) {
      getLogger().debug("could not fetch release info for {}: {}", imdbId, e.getMessage());
    }
  }

  /**
   * Fetches production companies for a given title ID and merges them into the metadata.
   * <p>
   * Uses the company credits endpoint with {@code categories=production} and paginates through all available results.
   * </p>
   *
   * @param md
   *          the {@link MediaMetadata} to merge production companies into
   * @param imdbId
   *          the IMDb title ID
   */
  protected void fetchAndMergeProductionCompanies(MediaMetadata md, String imdbId) {
    try {
      String pageToken = null;
      do {
        ImdbApiDevListTitleCompanyCreditsResponse response = api.titleService()
            .getCompanyCredits(imdbId, "production", 50, pageToken)
            .execute()
            .body();
        if (response == null || ListUtils.isEmpty(response.companyCredits)) {
          break;
        }

        for (ImdbApiDevCompanyCredit companyCredit : ListUtils.nullSafe(response.companyCredits)) {
          if (companyCredit == null || companyCredit.company == null || StringUtils.isBlank(companyCredit.company.displayName)) {
            continue;
          }
          md.addProductionCompany(companyCredit.company.displayName);
        }

        pageToken = response.nextPageToken;
      } while (StringUtils.isNotBlank(pageToken));
    }
    catch (Exception e) {
      getLogger().debug("could not fetch production companies for {}: {}", imdbId, e.getMessage());
    }
  }

  /**
   * Extract the country code from a release-info entry.
   *
   * @param releaseInfo
   *          the release-info entry
   * @return the country code or an empty string
   */
  private String getReleaseInfoCountryCode(ImdbApiDevReleaseDate releaseInfo) {
    if (releaseInfo == null) {
      return "";
    }

    if (StringUtils.isNotBlank(releaseInfo.countryCode)) {
      return releaseInfo.countryCode;
    }

    if (releaseInfo.country != null && StringUtils.isNotBlank(releaseInfo.country.code)) {
      return releaseInfo.country.code;
    }

    return "";
  }

  /**
   * Converts a precision date object into a {@link Date}.
   *
   * @param precisionDate
   *          the precision date
   * @return the converted {@link Date}, or {@code null} if year is missing
   */
  private Date toDate(ImdbApiDevPrecisionDate precisionDate) {
    if (precisionDate == null || precisionDate.year == null || precisionDate.year <= 0) {
      return null;
    }

    int month = precisionDate.month != null ? Math.max(1, Math.min(precisionDate.month, 12)) : 1;
    int day = precisionDate.day != null ? Math.max(1, Math.min(precisionDate.day, 31)) : 1;

    Calendar cal = Calendar.getInstance();
    cal.setLenient(false);
    cal.set(precisionDate.year, month - 1, day, 0, 0, 0);
    cal.set(Calendar.MILLISECOND, 0);

    try {
      return cal.getTime();
    }
    catch (Exception e) {
      return null;
    }
  }

  /**
   * Creates a {@link Person} from an {@link ImdbApiDevName} and adds it to the metadata.
   *
   * @param md
   *          the {@link MediaMetadata} to add the person to
   * @param name
   *          the {@link ImdbApiDevName} to map
   * @param type
   *          the {@link Person.Type}
   * @param role
   *          the role/character name, or {@code null}
   */
  private void addPersonToMetadata(MediaMetadata md, ImdbApiDevName name, Person.Type type, String role) {
    if (name == null || StringUtils.isBlank(name.displayName)) {
      return;
    }

    Person p = new Person(type, name.displayName);

    if (StringUtils.isNotBlank(name.id)) {
      p.setId(MediaMetadata.IMDB, name.id);
    }
    if (name.primaryImage != null && StringUtils.isNotBlank(name.primaryImage.url)) {
      p.setThumbUrl(name.primaryImage.url);
    }
    if (StringUtils.isNotBlank(role)) {
      p.setRole(role);
    }

    md.addCastMember(p);
  }

  /**
   * Maps an imdbapi.dev credit category string to a tinyMediaManager {@link Person.Type}.
   *
   * @param category
   *          the credit category string from the API (e.g. "actor", "director")
   * @return the matching {@link Person.Type}, or {@code null} if the category is unknown
   */
  private Person.Type mapCreditCategoryToPersonType(String category) {
    if (StringUtils.isBlank(category)) {
      return null;
    }
    return switch (category.toLowerCase()) {
      case "actor", "actress" -> Person.Type.ACTOR;
      case "director" -> Person.Type.DIRECTOR;
      case "writer" -> Person.Type.WRITER;
      case "producer" -> Person.Type.PRODUCER;
      case "composer" -> Person.Type.COMPOSER;
      default -> Person.Type.OTHER;
    };
  }
}
