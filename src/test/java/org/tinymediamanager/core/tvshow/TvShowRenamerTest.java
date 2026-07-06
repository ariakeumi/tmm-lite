package org.tinymediamanager.core.tvshow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.tinymediamanager.TmmOsUtils;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSeasonBannerNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSeasonFanartNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSeasonNfoNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSeasonPosterNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSeasonThumbNaming;
import org.tinymediamanager.core.tvshow.tasks.TvShowRenameTask;
import org.tinymediamanager.scraper.entities.MediaEpisodeGroup;
import org.tinymediamanager.scraper.entities.MediaEpisodeNumber;

public class TvShowRenamerTest extends BasicTvShowTest {

  @Override
  public void setup() throws Exception {
    super.setup();

    // load MI
    TmmOsUtils.loadNativeLibs();
  }

  private TvShow createSingleTvShow() {
    // setup dummy
    MediaFile dmf = new MediaFile(Paths.get("/path/to", "video.avi"));

    TvShow single = new TvShow();
    single.setTitle("singleshow");
    single.setYear(2009);
    single.setPath("singleshow");

    TvShowEpisode ep = new TvShowEpisode();
    ep.setTitle("singleEP");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 2));
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, 3, 4));
    ep.addToMediaFiles(dmf);
    ep.setTvShow(single);
    single.addEpisode(ep);

    return single;
  }

  private TvShow createMultiTvShow() {
    // setup dummy
    MediaFile dmf = new MediaFile(Paths.get("/path/to", "video.avi"));

    TvShow multi = new TvShow();
    multi.setTitle("multishow");
    multi.setYear(2009);
    multi.setPath("multishow");

    TvShowEpisode ep = new TvShowEpisode();
    ep.setTitle("multiEP2");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 2));
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, 3, 4));
    ep.addToMediaFiles(dmf);
    ep.setTvShow(multi);
    multi.addEpisode(ep);

    ep = new TvShowEpisode();
    ep.setTitle("multiEP3");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 3));
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, 3, 5));
    ep.addToMediaFiles(dmf);
    ep.setTvShow(multi);
    multi.addEpisode(ep);

    return multi;
  }

  private TvShow createNonContiguousMultiTvShow() {
    MediaFile dmf = new MediaFile(Paths.get("/path/to", "video.avi"));

    TvShow multi = new TvShow();
    multi.setTitle("multishow");
    multi.setYear(2009);
    multi.setPath("multishow");

    TvShowEpisode ep = new TvShowEpisode();
    ep.setTitle("multiEP2");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 2));
    ep.addToMediaFiles(dmf);
    ep.setTvShow(multi);
    multi.addEpisode(ep);

    ep = new TvShowEpisode();
    ep.setTitle("multiEP4");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 4));
    ep.addToMediaFiles(dmf);
    ep.setTvShow(multi);
    multi.addEpisode(ep);

    return multi;
  }

  private TvShow createCrossSeasonMultiTvShow() {
    MediaFile dmf = new MediaFile(Paths.get("/path/to", "video.avi"));

    TvShow multi = new TvShow();
    multi.setTitle("multishow");
    multi.setYear(2009);
    multi.setPath("multishow");

    TvShowEpisode ep = new TvShowEpisode();
    ep.setTitle("multiEP10");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 10));
    ep.addToMediaFiles(dmf);
    ep.setTvShow(multi);
    multi.addEpisode(ep);

    ep = new TvShowEpisode();
    ep.setTitle("multiEP201");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 2, 1));
    ep.addToMediaFiles(dmf);
    ep.setTvShow(multi);
    multi.addEpisode(ep);

    return multi;
  }

  private TvShow createDiscTvShow(String path) {
    TvShow disc = new TvShow();
    disc.setTitle(path);
    disc.setYear(2009);
    disc.setPath(getWorkFolder().resolve(path).toString());

    TvShowEpisode ep = new TvShowEpisode();
    ep.setPath(getWorkFolder().resolve(path).resolve("S01E07E08E09").toString());
    ep.setTvShow(disc);
    ep.setDisc(true);
    ep.setTitle("discfile");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 2));
    ep.addToMediaFiles(new MediaFile(getWorkFolder().resolve(path).resolve("S01E07E08E09/VIDEO_TS/VTS_01_1.VOB")));
    ep.addToMediaFiles(new MediaFile(getWorkFolder().resolve(path).resolve("S01E07E08E09/VIDEO_TS-thumb.jpg")));
    disc.addEpisode(ep);

    return disc;
  }

  private TvShow createDiscEpTvShow(String path) {

    TvShow discEP = new TvShow();
    discEP.setTitle(path);
    discEP.setYear(2009);
    discEP.setPath(getWorkFolder().resolve(path).toString());

    TvShowEpisode ep = new TvShowEpisode();
    ep.setPath(getWorkFolder().resolve(path).resolve("S01EP01 title").toString());
    ep.setTvShow(discEP);
    ep.setDisc(true);
    ep.setTitle("disc ep");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 1));
    ep.addToMediaFiles(new MediaFile(getWorkFolder().resolve(path).resolve("S01EP01 title/VTS_01_1.VOB")));
    discEP.addEpisode(ep);

    return discEP;
  }

  @Test
  public void tvRenamerPatterns() {
    TvShowRenamerProfile renamerProfile = TvShowModuleManager.getInstance().getSettings().getDefaultRenamerProfile();

    // SINGLE - RECOMMENDED
    TvShow single = createSingleTvShow();
    assertEqual(p("singleshow (2009)/Season 1/singleshow - S01E02 - singleEP.avi"), gen(single, "${showTitle} (${showYear})", "Season ${seasonNr}",
        "${showTitle} - S${seasonNr2}E${episodeNr2} - ${title}", true, renamerProfile));
    assertEqual(p("singleshow (2009)/Season 1/E02 - singleEP.avi"),
        gen(single, "${showTitle} (${showYear})", "Season ${seasonNr}", "E${episodeNr2} - ${title}", true, renamerProfile));
    assertEqual(p("singleshow (2009)/Season 1/S01E02 - singleEP.avi"),
        gen(single, "${showTitle} (${showYear})", "Season ${seasonNr}", "S${seasonNr2}E${episodeNr2} - ${title}", true, renamerProfile));
    assertEqual(p("singleshow (2009)/Season 1/1x04 - singleEP.avi"),
        gen(single, "${showTitle} (${showYear})", "Season ${seasonNr}", "${seasonNr}x${episodeNrDvd2} - ${title}", true, renamerProfile));
    assertEqual(p("singleshow (2009)/102 - singleEP.avi"),
        gen(single, "${showTitle} (${showYear})", "", "${seasonNr}${episodeNr2} - ${title}", true, renamerProfile));
    assertEqual(p("singleshow (2009)/1x04 - singleEP.avi"),
        gen(single, "${showTitle} (${showYear})", "", "${seasonNr}x${episodeNrDvd2} - ${title}", true, renamerProfile));
    assertEqual(p("singleshow (2009)/Season 1/E02 - singleEP.avi"),
        gen(single, "${showTitle} (${showYear})", "Season ${seasonNr}", "E${episodeNr2} - ${title} () [] {} ( ) [ ] { } ", true, renamerProfile));

    // SINGLE - not recommended, but working
    assertEqual(p("singleshow (2009)/Season 1/S01 - singleEP.avi"),
        gen(single, "${showTitle} (${showYear})", "Season ${seasonNr}", "S${seasonNr2} - ${title}", false, renamerProfile));
    assertEqual(p("singleshow (2009)/E02 - singleEP.avi"),
        gen(single, "${showTitle} (${showYear})", "", "E${episodeNr2} - ${title}", false, renamerProfile));
    assertEqual(p("singleshow (2009)/E02.avi"), gen(single, "${showTitle} (${showYear})", "", "E${episodeNr2}", false, renamerProfile));
    assertEqual(p("singleshow (2009)/Season 01/102 303- singleEP.avi"), gen(single, "${showTitle} (${showYear})", "Season ${seasonNr2}",
        "${seasonNr}${episodeNr2} ${seasonNrDvd}${seasonNrDvd2}- ${title}", false, renamerProfile));
    assertEqual(p("singleshow (2009)/Season 01/102 3x04- singleEP.avi"), gen(single, "${showTitle} (${showYear})", "Season ${seasonNr2}",
        "${seasonNr}${episodeNr2} ${seasonNrDvd}x${episodeNrDvd2}- ${title}", false, renamerProfile));
    assertEqual(p("singleshow (2009)/singleEP.avi"), gen(single, "${showTitle} (${showYear})", "", "${title}", false, renamerProfile));
    assertEqual(p("singleshow (2009)/singleEPsingleEP.avi"),
        gen(single, "${showTitle} (${showYear})", "", "${title}${title}", false, renamerProfile));
    assertEqual(p("singleshow (2009)/singleshow - S101E02 - singleEP.avi"),
        gen(single, "${showTitle} (${showYear})", "", "${showTitle} - S${seasonNr}${seasonNr2}E${episodeNr2} - ${title}", false, renamerProfile)); // double
    assertEqual(p("singleshow (2009)/singleshow - S1E0204 - singleEP.avi"),
        gen(single, "${showTitle} (${showYear})", "", "${showTitle} - S${seasonNr}E${episodeNr2}${episodeNrDvd2} - ${title}", false, renamerProfile)); // double

    // *******************
    // COPY 1:1 FROM ABOVE
    // *******************

    // MULTI - RECOMMENDED
    TvShow multi = createMultiTvShow();
    assertEqual(p("multishow (2009)/Season 1/multishow - S01E02 S01E03 - multiEP2 - multiEP3.avi"), gen(multi, "${showTitle} (${showYear})",
        "Season ${seasonNr}", "${showTitle} - S${seasonNr2}E${episodeNr2} - ${title}", true, renamerProfile));
    assertEqual(p("multishow (2009)/Season 1/E02 E03 - multiEP2 - multiEP3.avi"),
        gen(multi, "${showTitle} (${showYear})", "Season ${seasonNr}", "E${episodeNr2} - ${title}", true, renamerProfile));
    assertEqual(p("multishow (2009)/Season 1/S01E02 S01E03 - multiEP2 - multiEP3.avi"),
        gen(multi, "${showTitle} (${showYear})", "Season ${seasonNr}", "S${seasonNr2}E${episodeNr2} - ${title}", true, renamerProfile));
    assertEqual(p("multishow (2009)/Season 1/1x04 1x05 - multiEP2 - multiEP3.avi"),
        gen(multi, "${showTitle} (${showYear})", "Season ${seasonNr}", "${seasonNr}x${episodeNrDvd2} - ${title}", true, renamerProfile));
    assertEqual(p("multishow (2009)/102 103 - multiEP2 - multiEP3.avi"),
        gen(multi, "${showTitle} (${showYear})", "", "${seasonNr}${episodeNr2} - ${title}", true, renamerProfile));
    assertEqual(p("multishow (2009)/1x04 1x05 - multiEP2 - multiEP3.avi"),
        gen(multi, "${showTitle} (${showYear})", "", "${seasonNr}x${episodeNrDvd2} - ${title}", true, renamerProfile));
    assertEqual(p("multishow (2009)/Season 1/E02 E03 - multiEP2 - multiEP3.avi"),
        gen(multi, "${showTitle} (${showYear})", "Season ${seasonNr}", "E${episodeNr2} - ${title} () [] {} ( ) [ ] { } ", true, renamerProfile));

    // MULTI - not recommended, but working
    assertEqual(p("multishow (2009)/Season 1/S01 S01 - multiEP2 - multiEP3.avi"),
        gen(multi, "${showTitle} (${showYear})", "Season ${seasonNr}", "S${seasonNr2} - ${title}", false, renamerProfile));
    assertEqual(p("multishow (2009)/E02 E03 - multiEP2 - multiEP3.avi"),
        gen(multi, "${showTitle} (${showYear})", "", "E${episodeNr2} - ${title}", false, renamerProfile));
    assertEqual(p("multishow (2009)/E02 E03.avi"), gen(multi, "${showTitle} (${showYear})", "", "E${episodeNr2}", false, renamerProfile));
    assertEqual(p("multishow (2009)/Season 01/102 103 303 - multiEP2 - multiEP3.avi"), gen(multi, "${showTitle} (${showYear})", "Season ${seasonNr2}",
        "${seasonNr}${episodeNr2} ${seasonNrDvd}${seasonNrDvd2} - ${title}", false, renamerProfile));
    // assertEqual(p("multishow (2009)/Season 01/102 103 3x04 - multiEP2 - multiEP3.avi"),
    // gen(multi, "${showTitle} (${showYear})", "Season ${seasonNr2}", "${seasonNr}${episodeNr2} ${seasonNrDvd}x${episodeNrDvd2} - ${title}", false));
    assertEqual(p("multishow (2009)/multiEP2 - multiEP3.avi"), gen(multi, "${showTitle} (${showYear})", "", "${title}", false, renamerProfile));
    assertEqual(p("multishow (2009)/multiEP2 - multiEP3multiEP2 - multiEP3.avi"),
        gen(multi, "${showTitle} (${showYear})", "", "${title}${title}", false, renamerProfile));
    assertEqual(p("multishow (2009)/multishow - S101E02 - multiEP2 - multiEP3.avi"),
        gen(multi, "${showTitle} (${showYear})", "", "${showTitle} - S${seasonNr}${seasonNr2}E${episodeNr2} - ${title}", false, renamerProfile)); // double
    assertEqual(p("multishow (2009)/multishow - S1E02 S1E0304 - multiEP2 - multiEP3.avi"),
        gen(multi, "${showTitle} (${showYear})", "", "${showTitle} - S${seasonNr}E${episodeNr2}${episodeNrDvd2} - ${title}", false, renamerProfile)); // double
  }

  @Test
  public void testDiscEpisode() throws Exception {
    copyResourceFolderToWorkFolder("testtvshows");
    TvShowRenamerProfile renamerProfile = new TvShowRenamerProfile();

    TvShow disc = createDiscTvShow("testtvshows/Janosik DVD");
    TvShowRenamer.renameEpisode(disc.getEpisode(1, 2).get(0), renamerProfile);

    TvShow discEp = createDiscEpTvShow("testtvshows/DVDEpisodeInRoot");
    TvShowRenamer.renameEpisode(discEp.getEpisode(1, 1).get(0), renamerProfile);
  }

  @Test
  public void testMultiEpisodeRangeStyle() {
    TvShowRenamerProfile renamerProfile = new TvShowRenamerProfile();
    renamerProfile.setRenamerMultiEpisodeStyle(TvShowMultiEpisodeStyle.RANGE);

    TvShow multi = createMultiTvShow();
    assertEqual("multishow - S01E02-E03 - multiEP2 - multiEP3",
        TvShowRenamer.createDestination("${showTitle} - S${seasonNr2}E${episodeNr2} - ${title}", multi.getEpisodes(), renamerProfile));
    assertEqual("E02-E03 - multiEP2 - multiEP3", TvShowRenamer.createDestination("E${episodeNr2} - ${title}", multi.getEpisodes(), renamerProfile));
    assertEqual("1x04-05 - multiEP2 - multiEP3",
        TvShowRenamer.createDestination("${seasonNr}x${episodeNrDvd2} - ${title}", multi.getEpisodes(), renamerProfile));

    TvShow single = createSingleTvShow();
    assertEqual("singleshow - S01E02 - singleEP",
        TvShowRenamer.createDestination("${showTitle} - S${seasonNr2}E${episodeNr2} - ${title}", single.getEpisodes(), renamerProfile));
  }

  @Test
  public void testMultiEpisodeRangeFallbackForNonContiguousEpisodes() {
    TvShowRenamerProfile renamerProfile = TvShowModuleManager.getInstance().getSettings().getDefaultRenamerProfile();
    renamerProfile.setRenamerMultiEpisodeStyle(TvShowMultiEpisodeStyle.RANGE);

    TvShow multi = createNonContiguousMultiTvShow();
    assertEqual("multishow - S01E02 S01E04 - multiEP2 - multiEP4",
        TvShowRenamer.createDestination("${showTitle} - S${seasonNr2}E${episodeNr2} - ${title}", multi.getEpisodes(), renamerProfile));
  }

  @Test
  public void testMultiEpisodeRangeFallbackForCrossSeasonEpisodes() {
    TvShowRenamerProfile renamerProfile = TvShowModuleManager.getInstance().getSettings().getDefaultRenamerProfile();
    TvShowModuleManager.getInstance().getSettings().getDefaultRenamerProfile().setRenamerMultiEpisodeStyle(TvShowMultiEpisodeStyle.RANGE);

    TvShow multi = createCrossSeasonMultiTvShow();
    assertEqual("multishow - S01E10 S02E01 - multiEP10 - multiEP201",
        TvShowRenamer.createDestination("${showTitle} - S${seasonNr2}E${episodeNr2} - ${title}", multi.getEpisodes(), renamerProfile));
  }

  private Path gen(TvShow show, String showPattern, String seasonPattern, String filePattern, boolean recommended,
      TvShowRenamerProfile renamerProfile) {
    Assert.assertEquals(recommended, TvShowRenamer.isRecommended(seasonPattern, filePattern));
    String sh = TvShowRenamer.getTvShowFoldername(showPattern, show, renamerProfile);
    String se = TvShowRenamer.getSeasonFoldername(seasonPattern, show, show.getEpisodes().get(0), renamerProfile);
    String ep = TvShowRenamer.generateEpisodeFilenames(filePattern, show, show.getEpisodesMediaFiles().get(0), "", renamerProfile)
        .get(0)
        .getFilename();
    System.out.println(new File(sh, se + File.separator + ep));
    // return new File(sh, se + File.separator + ep).toString();
    return Paths.get(sh, se, ep);
  }

  private Path p(String path) {
    return Paths.get(path);
  }

  private void checkFiles(Path moviePath, String... filenames) {
    for (String filename : filenames) {
      Path filePath = moviePath.resolve(filename);
      assertThat(filePath).exists();
    }
  }

  /**
   * just a test of a simple episode (one EP file with some extra files)
   */
  @Test
  public void testSimpleEpisode() throws Exception {
    // copy over the test files to a new folder
    copyResourceFolderToWorkFolder("testtvshows/renamer_test/simple");

    Path source = getWorkFolder().resolve("testtvshows/renamer_test/simple");
    Path destination = getWorkFolder().resolve("tv_show_renamer_simple/ShowForRenamer");
    FileUtils.copyDirectory(source.toFile(), destination.toFile());

    TvShow show = new TvShow();
    show.setTitle("Breaking Bad");
    show.setYear(2008);
    show.setDataSource(destination.getParent().toAbsolutePath().toString());
    show.setPath(destination.toAbsolutePath().toString());

    TvShowList.getInstance().addTvShow(show);

    // classical single file episode
    TvShowEpisode ep = new TvShowEpisode();
    ep.setTitle("Pilot");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 1));
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, 1, 1));
    ep.setPath(destination.toAbsolutePath().toString());
    MediaFile mf = new MediaFile(destination.resolve("S01E01.jpg").toAbsolutePath(), MediaFileType.THUMB);
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01.mkv").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01.nfo").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01.de.srt").toAbsolutePath());
    ep.addToMediaFiles(mf);
    ep.setTvShow(show);
    ep.gatherMediaFileInformation(false); // add langu from filenames
    show.addEpisode(ep);

    Path tvShowPath = destination;
    String[] filenamesOld = new String[] { "S01E01.de.srt", "S01E01.jpg", "S01E01.nfo", "S01E01.mkv" };
    checkFiles(tvShowPath, filenamesOld);

    renameTvShow(show);

    Path showDir = destination.getParent().resolve("Breaking Bad (2008)");
    assertThat(showDir).exists();

    String[] filenamesNew = new String[] { "Season 1/Breaking Bad - S01E01 - Pilot.mkv", "Season 1/Breaking Bad - S01E01 - Pilot-thumb.jpg",
        "Season 1/Breaking Bad - S01E01 - Pilot.nfo", "Season 1/Breaking Bad - S01E01 - Pilot.deu.srt" };
    checkFiles(showDir, filenamesNew);

    // undo
    TvShowRenamer.undoRename(show);
    show.getEpisodes().forEach(TvShowRenamer::undoRename);
    checkFiles(tvShowPath, filenamesOld);
  }

  /**
   * just a test of a simple episode with extras (two EP files with some extra files)
   */
  @Test
  public void testSimpleEpisodeWithExtras() throws Exception {
    // copy over the test files to a new folder
    copyResourceFolderToWorkFolder("testtvshows/renamer_test/extra");

    Path source = getWorkFolder().resolve("testtvshows/renamer_test/extra");
    Path destination = getWorkFolder().resolve("tv_show_renamer_simple/ShowForRenamer");
    FileUtils.copyDirectory(source.toFile(), destination.toFile());

    TvShow show = new TvShow();
    show.setTitle("Breaking Bad");
    show.setYear(2008);
    show.setDataSource(destination.getParent().toAbsolutePath().toString());
    show.setPath(destination.toAbsolutePath().toString());

    TvShowList.getInstance().addTvShow(show);

    MediaFile mf = new MediaFile(destination.resolve("extras/Show extra.avi").toAbsolutePath());
    mf.gatherMediaInformation();
    show.addToMediaFiles(mf);

    // classical single file episodes with extras
    TvShowEpisode ep = new TvShowEpisode();
    ep.setTitle("Pilot");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 1));
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, 1, 1));
    ep.setPath(destination.toAbsolutePath().toString());

    mf = new MediaFile(destination.resolve("S01E01.mkv").toAbsolutePath());
    ep.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("extras/S01E01 - cut scenes.mkv").toAbsolutePath(), MediaFileType.EXTRA);
    ep.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("S01E01 - sample.avi").toAbsolutePath(), MediaFileType.SAMPLE);
    ep.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("S01E01 - something else.mkv").toAbsolutePath(), MediaFileType.EXTRA);
    ep.addToMediaFiles(mf);

    ep.gatherMediaFileInformation(false); // add langu from filenames
    ep.setTvShow(show);
    show.addEpisode(ep);

    ep = new TvShowEpisode();
    ep.setTitle("Pilot 2");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 2));
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, 1, 2));
    ep.setPath(destination.toAbsolutePath().toString());
    mf = new MediaFile(destination.resolve("Season 1/S01E02.mkv").toAbsolutePath());
    ep.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("Season 1/extras/S01E02 - takeouts.mkv").toAbsolutePath(), MediaFileType.EXTRA);
    ep.addToMediaFiles(mf);

    ep.gatherMediaFileInformation(false); // add langu from filenames
    ep.setTvShow(show);
    show.addEpisode(ep);

    Path tvShowPath = destination;
    String[] filenamesOld = new String[] { "S01E01.mkv", "extras/S01E01 - cut scenes.mkv", "S01E01 - sample.avi", "S01E01 - something else.mkv",
        "Season 1/S01E02.mkv", "Season 1/extras/S01E02 - takeouts.mkv" };
    checkFiles(tvShowPath, filenamesOld);

    renameTvShow(show);

    Path showDir = destination.getParent().resolve("Breaking Bad (2008)");
    assertThat(showDir).exists();

    String[] filenamesNew = new String[] { "Season 1/Breaking Bad - S01E01 - Pilot.mkv", "extras/S01E01 - cut scenes.mkv",
        "Season 1/Breaking Bad - S01E01 - Pilot-sample.avi", "Season 1/Breaking Bad - S01E01 - Pilot - something else.mkv",
        "Season 1/Breaking Bad - S01E02 - Pilot 2.mkv", "Season 1/extras/S01E02 - takeouts.mkv" };
    checkFiles(showDir, filenamesNew);

    // undo
    TvShowRenamer.undoRename(show);
    show.getEpisodes().forEach(TvShowRenamer::undoRename);
    checkFiles(tvShowPath, filenamesOld);
  }

  /**
   * multi episode file test
   */
  @Test
  public void testMultiEpisode() throws Exception {
    // copy over the test files to a new folder
    copyResourceFolderToWorkFolder("testtvshows/renamer_test/multi");

    Path source = getWorkFolder().resolve("testtvshows/renamer_test/multi");
    Path destination = getWorkFolder().resolve("tv_show_renamer_simple/ShowForRenamer");
    FileUtils.copyDirectory(source.toFile(), destination.toFile());

    TvShow show = new TvShow();
    show.setTitle("Breaking Bad");
    show.setYear(2008);
    show.setDataSource(destination.getParent().toAbsolutePath().toString());
    show.setPath(destination.toAbsolutePath().toString());

    TvShowList.getInstance().addTvShow(show);

    // multi episode file
    TvShowEpisode ep = new TvShowEpisode();
    ep.setTitle("Pilot");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 1));
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, 1, 1));
    ep.setPath(destination.toAbsolutePath().toString());
    MediaFile mf = new MediaFile(destination.resolve("S01E01E02.jpg").toAbsolutePath(), MediaFileType.THUMB);
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.mkv").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.nfo").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.de.srt").toAbsolutePath());
    ep.addToMediaFiles(mf);
    ep.gatherMediaFileInformation(false); // add langu from filenames
    ep.setTvShow(show);
    show.addEpisode(ep);

    ep = new TvShowEpisode();
    ep.setTitle("Pilot 2");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 2));
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, 1, 2));
    ep.setPath(destination.toAbsolutePath().toString());
    mf = new MediaFile(destination.resolve("S01E01E02.jpg").toAbsolutePath(), MediaFileType.THUMB);
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.mkv").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.nfo").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.de.srt").toAbsolutePath());
    ep.addToMediaFiles(mf);
    ep.gatherMediaFileInformation(false); // add langu from filenames
    ep.setTvShow(show);
    show.addEpisode(ep);

    Path tvShowPath = destination;
    String[] filenamesOld = new String[] { "S01E01E02.mkv", "S01E01E02.jpg", "S01E01E02.nfo", "S01E01E02.de.srt" };
    checkFiles(tvShowPath, filenamesOld);

    renameTvShow(show);

    Path showDir = destination.getParent().resolve("Breaking Bad (2008)");
    assertThat(showDir).exists();

    String[] filenamesNew = new String[] { "Season 1/Breaking Bad - S01E01 S01E02 - Pilot - Pilot 2.mkv",
        "Season 1/Breaking Bad - S01E01 S01E02 - Pilot - Pilot 2-thumb.jpg", "Season 1/Breaking Bad - S01E01 S01E02 - Pilot - Pilot 2.nfo",
        "Season 1/Breaking Bad - S01E01 S01E02 - Pilot - Pilot 2.deu.srt" };
    checkFiles(showDir, filenamesNew);

    // undo
    TvShowRenamer.undoRename(show);
    show.getEpisodes().forEach(TvShowRenamer::undoRename);
    checkFiles(tvShowPath, filenamesOld);
  }

  /**
   * just a test of a parted episode (two EP files with some extra files)
   */
  @Test
  public void testPartedEpisode() throws Exception {
    // copy over the test files to a new folder
    copyResourceFolderToWorkFolder("testtvshows/renamer_test/parted");

    Path source = getWorkFolder().resolve("testtvshows/renamer_test/parted");
    Path destination = getWorkFolder().resolve("tv_show_renamer_simple/ShowForRenamer");
    FileUtils.copyDirectory(source.toFile(), destination.toFile());

    TvShow show = new TvShow();
    show.setTitle("Breaking Bad");
    show.setYear(2008);
    show.setDataSource(destination.getParent().toAbsolutePath().toString());
    show.setPath(destination.toAbsolutePath().toString());

    TvShowList.getInstance().addTvShow(show);

    // classical single file episode
    TvShowEpisode ep = new TvShowEpisode();
    ep.setTitle("Pilot");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 1));
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, 1, 1));
    ep.setPath(destination.toAbsolutePath().toString());
    MediaFile mf = new MediaFile(destination.resolve("S01E01.jpg").toAbsolutePath(), MediaFileType.THUMB);
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01.part1.mkv").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01.part2.mkv").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01.nfo").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01.de.srt").toAbsolutePath());
    ep.addToMediaFiles(mf);
    ep.setTvShow(show);
    ep.reEvaluateStacking();
    ep.gatherMediaFileInformation(false); // add langu from filenames
    show.addEpisode(ep);

    Path tvShowPath = destination;
    String[] filenamesOld = new String[] { "S01E01.part1.mkv", "S01E01.part2.mkv", "S01E01.jpg", "S01E01.nfo", "S01E01.de.srt" };
    checkFiles(tvShowPath, filenamesOld);

    renameTvShow(show);

    Path showDir = destination.getParent().resolve("Breaking Bad (2008)");
    assertThat(showDir).exists();

    String[] filenamesNew = new String[] { "Season 1/Breaking Bad - S01E01 - Pilot.part1.mkv", "Season 1/Breaking Bad - S01E01 - Pilot.part2.mkv",
        "Season 1/Breaking Bad - S01E01 - Pilot-thumb.jpg", "Season 1/Breaking Bad - S01E01 - Pilot.nfo",
        "Season 1/Breaking Bad - S01E01 - Pilot.deu.srt" };
    checkFiles(showDir, filenamesNew);

    // undo
    TvShowRenamer.undoRename(show);
    show.getEpisodes().forEach(TvShowRenamer::undoRename);
    checkFiles(tvShowPath, filenamesOld);
  }

  /**
   * this is a really sick test: a parted multi episode (two EP files containing two EPs with some extra files)
   */
  @Test
  public void testComplexEpisode() throws Exception {
    // copy over the test files to a new folder
    copyResourceFolderToWorkFolder("testtvshows/renamer_test/complex");

    Path source = getWorkFolder().resolve("testtvshows/renamer_test/complex");
    Path destination = getWorkFolder().resolve("tv_show_renamer_simple/ShowForRenamer");
    FileUtils.copyDirectory(source.toFile(), destination.toFile());

    TvShow show = new TvShow();
    show.setTitle("Breaking Bad");
    show.setYear(2008);
    show.setDataSource(destination.getParent().toAbsolutePath().toString());
    show.setPath(destination.toAbsolutePath().toString());

    TvShowList.getInstance().addTvShow(show);

    // classical single file episode
    TvShowEpisode ep = new TvShowEpisode();
    ep.setTitle("Pilot");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 1));
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, 1, 1));
    ep.setPath(destination.toAbsolutePath().toString());
    MediaFile mf = new MediaFile(destination.resolve("S01E01E02.jpg").toAbsolutePath(), MediaFileType.THUMB);
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.part1.mkv").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.part2.mkv").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.nfo").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.de.srt").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.sub").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.idx").toAbsolutePath());
    ep.addToMediaFiles(mf);
    ep.setTvShow(show);
    ep.reEvaluateStacking();
    ep.gatherMediaFileInformation(false); // add langu from filenames
    show.addEpisode(ep);

    ep = new TvShowEpisode();
    ep.setTitle("Pilot 2");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 2));
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, 1, 1));
    ep.setPath(destination.toAbsolutePath().toString());
    mf = new MediaFile(destination.resolve("S01E01E02.jpg").toAbsolutePath(), MediaFileType.THUMB);
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.part1.mkv").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.part2.mkv").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.nfo").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.de.srt").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.sub").toAbsolutePath());
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01E02.idx").toAbsolutePath());
    ep.addToMediaFiles(mf);
    ep.setTvShow(show);
    ep.reEvaluateStacking();
    ep.gatherMediaFileInformation(false); // add langu from filenames
    show.addEpisode(ep);

    Path tvShowPath = destination;
    String[] filenamesOld = new String[] { "S01E01E02.part1.mkv", "S01E01E02.part2.mkv", "S01E01E02.jpg", "S01E01E02.nfo", "S01E01E02.de.srt",
        "S01E01E02.sub", "S01E01E02.idx" };
    checkFiles(tvShowPath, filenamesOld);

    renameTvShow(show);

    Path showDir = destination.getParent().resolve("Breaking Bad (2008)");
    assertThat(showDir).exists();

    String[] filenamesNew = new String[] { "Season 1/Breaking Bad - S01E01 S01E02 - Pilot - Pilot 2.part1.mkv",
        "Season 1/Breaking Bad - S01E01 S01E02 - Pilot - Pilot 2.part1.mkv", "Season 1/Breaking Bad - S01E01 S01E02 - Pilot - Pilot 2-thumb.jpg",
        "Season 1/Breaking Bad - S01E01 S01E02 - Pilot - Pilot 2.nfo", "Season 1/Breaking Bad - S01E01 S01E02 - Pilot - Pilot 2.deu.srt",
        "Season 1/Breaking Bad - S01E01 S01E02 - Pilot - Pilot 2.sub", "Season 1/Breaking Bad - S01E01 S01E02 - Pilot - Pilot 2.idx" };
    checkFiles(showDir, filenamesNew);

    // undo
    TvShowRenamer.undoRename(show);
    show.getEpisodes().forEach(TvShowRenamer::undoRename);
    checkFiles(tvShowPath, filenamesOld);
  }

  /**
   * just a test of a simple episode (two EPs file with some season artwork)
   */
  @Test
  public void testSimpleEpisodeWithSeasonArtwork() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.addSeasonPosterFilename(TvShowSeasonPosterNaming.SEASON_FOLDER);
    settings.addSeasonBannerFilename(TvShowSeasonBannerNaming.SEASON_FOLDER);

    // copy over the test files to a new folder
    copyResourceFolderToWorkFolder("testtvshows/renamer_test/season_artwork");

    Path source = getWorkFolder().resolve("testtvshows/renamer_test/season_artwork");
    Path destination = getWorkFolder().resolve("tv_show_renamer_simple/ShowForRenamer");
    FileUtils.copyDirectory(source.toFile(), destination.toFile());

    TvShow show = new TvShow();
    show.setTitle("Breaking Bad");
    show.setYear(2008);
    show.setDataSource(destination.getParent().toAbsolutePath().toString());
    show.setPath(destination.toAbsolutePath().toString());

    TvShowList.getInstance().addTvShow(show);

    // season artwork
    MediaFile mf = new MediaFile(destination.resolve("season01-banner.jpg").toAbsolutePath(), MediaFileType.SEASON_BANNER);
    mf.gatherMediaInformation();
    TvShowSeason tvShowSeason = show.getOrCreateSeason(1);
    tvShowSeason.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("season01-poster.jpg").toAbsolutePath(), MediaFileType.SEASON_POSTER);
    mf.gatherMediaInformation();
    tvShowSeason = show.getOrCreateSeason(1);
    tvShowSeason.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("season01-fanart.jpg").toAbsolutePath(), MediaFileType.SEASON_FANART);
    mf.gatherMediaInformation();
    tvShowSeason = show.getOrCreateSeason(1);
    tvShowSeason.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("season01-thumb.jpg").toAbsolutePath(), MediaFileType.SEASON_THUMB);
    mf.gatherMediaInformation();
    tvShowSeason = show.getOrCreateSeason(1);
    tvShowSeason.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("Season 2/season02-banner.jpg").toAbsolutePath(), MediaFileType.SEASON_BANNER);
    mf.gatherMediaInformation();
    tvShowSeason = show.getOrCreateSeason(2);
    tvShowSeason.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("Season 2/season02.jpg").toAbsolutePath(), MediaFileType.SEASON_POSTER);
    mf.gatherMediaInformation();
    tvShowSeason = show.getOrCreateSeason(2);
    tvShowSeason.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("Season 2/season02-fanart.jpg").toAbsolutePath(), MediaFileType.SEASON_FANART);
    mf.gatherMediaInformation();
    tvShowSeason = show.getOrCreateSeason(2);
    tvShowSeason.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("Season 2/season02-thumb.jpg").toAbsolutePath(), MediaFileType.SEASON_THUMB);
    mf.gatherMediaInformation();
    tvShowSeason = show.getOrCreateSeason(2);
    tvShowSeason.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("season03-banner.jpg").toAbsolutePath(), MediaFileType.SEASON_BANNER);
    mf.gatherMediaInformation();
    tvShowSeason = show.getOrCreateSeason(3);
    tvShowSeason.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("season03-poster.jpg").toAbsolutePath(), MediaFileType.SEASON_POSTER);
    mf.gatherMediaInformation();
    tvShowSeason = show.getOrCreateSeason(3);
    tvShowSeason.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("season03-fanart.jpg").toAbsolutePath(), MediaFileType.SEASON_FANART);
    mf.gatherMediaInformation();
    tvShowSeason = show.getOrCreateSeason(3);
    tvShowSeason.addToMediaFiles(mf);

    mf = new MediaFile(destination.resolve("season03-thumb.jpg").toAbsolutePath(), MediaFileType.SEASON_THUMB);
    mf.gatherMediaInformation();
    tvShowSeason = show.getOrCreateSeason(3);
    tvShowSeason.addToMediaFiles(mf);

    // classical single file episode
    TvShowEpisode ep = new TvShowEpisode();
    ep.setTvShow(show);
    ep.setTitle("Pilot");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 1));
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, 1, 1));
    ep.setPath(destination.toAbsolutePath().toString());
    mf = new MediaFile(destination.resolve("S01E01.mkv").toAbsolutePath());
    mf.gatherMediaInformation();
    ep.addToMediaFiles(mf);
    show.addEpisode(ep);

    ep = new TvShowEpisode();
    ep.setTvShow(show);
    ep.setTitle("Pilot 2");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 2, 1));
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, 2, 1));
    ep.setPath(destination.toAbsolutePath().toString());
    mf = new MediaFile(destination.resolve("Season 2/S02E01.mkv").toAbsolutePath());
    mf.gatherMediaInformation();
    ep.addToMediaFiles(mf);
    show.addEpisode(ep);

    renameTvShow(show);

    // check TV show dirs/files
    Path showDir = destination.getParent().resolve("Breaking Bad (2008)");
    assertThat(showDir).exists();

    Path season1Dir = showDir.resolve("Season 1");
    assertThat(season1Dir).exists();
    Path season2Dir = showDir.resolve("Season 2");
    assertThat(season2Dir).exists();

    // season 1 & 2 artwork in season folder and show folder
    Path artwork = season1Dir.resolve("season01-poster.jpg");
    assertThat(artwork).exists();
    artwork = season1Dir.resolve("season01-banner.jpg");
    assertThat(artwork).exists();

    artwork = showDir.resolve("season01-poster.jpg");
    assertThat(artwork).exists();
    artwork = showDir.resolve("season01-fanart.jpg");
    assertThat(artwork).exists();
    artwork = showDir.resolve("season01-banner.jpg");
    assertThat(artwork).exists();
    artwork = showDir.resolve("season01-thumb.jpg");
    assertThat(artwork).exists();

    artwork = season2Dir.resolve("season02-poster.jpg");
    assertThat(artwork).exists();
    artwork = season2Dir.resolve("season02-banner.jpg");
    assertThat(artwork).exists();

    artwork = showDir.resolve("season02-poster.jpg");
    assertThat(artwork).exists();
    artwork = showDir.resolve("season02-fanart.jpg");
    assertThat(artwork).exists();
    artwork = showDir.resolve("season02-banner.jpg");
    assertThat(artwork).exists();
    artwork = showDir.resolve("season02-thumb.jpg");
    assertThat(artwork).exists();

    // season 3 artwork will not be written because not activated in the settings
    // AND since the banner is not in a season folder, it will have the default name style applied
    artwork = showDir.resolve("season03-poster.jpg");
    assertThat(artwork).doesNotExist();
    artwork = showDir.resolve("season03-fanart.jpg");
    assertThat(artwork).doesNotExist();
    artwork = showDir.resolve("season03-banner.jpg");
    assertThat(artwork).doesNotExist();
    artwork = showDir.resolve("season03-thumb.jpg");
    assertThat(artwork).doesNotExist();

    // check episode dirs/files
    Path video = season1Dir.resolve("Breaking Bad - S01E01 - Pilot.mkv");
    assertThat(video).exists();
    video = season2Dir.resolve("Breaking Bad - S02E01 - Pilot 2.mkv");
    assertThat(video).exists();
  }

  private void renameTvShow(TvShow tvShow) {
    TvShowRenameTask task = new TvShowRenameTask(Collections.singletonList(tvShow), tvShow.getEpisodes(),
        TvShowModuleManager.getInstance().getSettings().getDefaultRenamerProfile());
    task.run(); // blocking
  }

  /**
   * Helper method to create a TV show with season artwork for testing season file naming patterns. Follows the same setup pattern as
   * testSimpleEpisodeWithSeasonArtwork.
   */
  private TvShow createShowWithSeasonArtwork(String testName, MediaFileType artworkType, String extension) throws Exception {
    Path destination = getWorkFolder().resolve("season_naming").resolve(testName);
    FileUtils.deleteDirectory(destination.toFile());
    FileUtils.forceMkdir(destination.toFile());

    TvShow show = new TvShow();
    show.setTitle("Breaking Bad");
    show.setYear(2008);
    show.setDataSource(destination.getParent().toAbsolutePath().toString());
    show.setPath(destination.toAbsolutePath().toString());
    TvShowList.getInstance().addTvShow(show);

    // Create episodes for seasons 0 and 1 to drive the renamer
    TvShowEpisode ep0 = new TvShowEpisode();
    ep0.setTvShow(show);
    ep0.setTitle("Special 1");
    ep0.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 0, 1));
    MediaFile mf0 = new MediaFile(destination.resolve("S00E01.mkv").toAbsolutePath());
    ep0.addToMediaFiles(mf0);
    show.addEpisode(ep0);

    TvShowEpisode ep1 = new TvShowEpisode();
    ep1.setTvShow(show);
    ep1.setTitle("Pilot");
    ep1.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 1));
    MediaFile mf1 = new MediaFile(destination.resolve("S01E01.mkv").toAbsolutePath());
    ep1.addToMediaFiles(mf1);
    show.addEpisode(ep1);

    // Attach season artwork sources for seasons 0 and 1
    for (int seasonNr : Arrays.asList(0, 1)) {
      TvShowSeason season = show.getOrCreateSeason(seasonNr);
      String artworkName = String.format("season%02d-%s.%s", seasonNr, artworkType.name().toLowerCase(Locale.ROOT).replace("season_", ""), extension);
      Path artworkPath = destination.resolve(artworkName).toAbsolutePath();

      // Create the artwork file
      FileUtils.forceMkdirParent(artworkPath.toFile());
      FileUtils.writeStringToFile(artworkPath.toFile(), "", StandardCharsets.UTF_8);

      MediaFile mf = new MediaFile(artworkPath, artworkType);
      mf.gatherMediaInformation();
      season.addToMediaFiles(mf);
    }

    return show;
  }

  /**
   * Helper method to verify that expected season artwork files exist after renaming
   */
  private void assertSeasonArtworkExists(TvShow show, int seasonNr, String expectedFilename) {
    if (expectedFilename == null || expectedFilename.isEmpty()) {
      return;
    }
    Path showDir = Paths.get(show.getPath());
    Path target = showDir.resolve(expectedFilename);
    assertThat(target).as("Season %d artwork file should exist: %s", seasonNr, expectedFilename).exists();
  }

  // =====================================================================================
  // Season Poster Naming Tests
  // =====================================================================================

  @Test
  public void testSeasonPosterNaming_SEASON_POSTER() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonPosterFilenames();
    settings.addSeasonPosterFilename(TvShowSeasonPosterNaming.SEASON_POSTER);
    settings.getDefaultRenamerProfile().setSpecialSeason(true);

    TvShow show = createShowWithSeasonArtwork("poster_season_poster", MediaFileType.SEASON_POSTER, "jpg");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "season-specials-poster.jpg");
    assertSeasonArtworkExists(show, 1, "season01-poster.jpg");
  }

  @Test
  public void testSeasonPosterNaming_SEASON_FOLDER() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonPosterFilenames();
    settings.addSeasonPosterFilename(TvShowSeasonPosterNaming.SEASON_FOLDER);
    settings.getDefaultRenamerProfile().setSpecialSeason(true);

    TvShow show = createShowWithSeasonArtwork("poster_season_folder", MediaFileType.SEASON_POSTER, "jpg");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "Specials/season-specials-poster.jpg");
    assertSeasonArtworkExists(show, 1, "Season 1/season01-poster.jpg");
  }

  @Test
  public void testSeasonPosterNaming_SEASON_FOLDER2() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonPosterFilenames();
    settings.addSeasonPosterFilename(TvShowSeasonPosterNaming.SEASON_FOLDER2);
    settings.getDefaultRenamerProfile().setSpecialSeason(true);

    TvShow show = createShowWithSeasonArtwork("poster_season_folder2", MediaFileType.SEASON_POSTER, "jpg");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "Specials/season-specials.jpg");
    assertSeasonArtworkExists(show, 1, "Season 1/season01.jpg");
  }

  @Test
  public void testSeasonPosterNaming_FOLDER() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonPosterFilenames();
    settings.addSeasonPosterFilename(TvShowSeasonPosterNaming.FOLDER);
    settings.getDefaultRenamerProfile().setSpecialSeason(true);

    TvShow show = createShowWithSeasonArtwork("poster_folder", MediaFileType.SEASON_POSTER, "jpg");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "Specials/folder.jpg");
    assertSeasonArtworkExists(show, 1, "Season 1/folder.jpg");
  }

  @Test
  public void testSeasonPosterNaming_POSTER() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonPosterFilenames();
    settings.addSeasonPosterFilename(TvShowSeasonPosterNaming.POSTER);
    settings.getDefaultRenamerProfile().setSpecialSeason(true);

    TvShow show = createShowWithSeasonArtwork("poster_poster", MediaFileType.SEASON_POSTER, "jpg");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "Specials/poster.jpg");
    assertSeasonArtworkExists(show, 1, "Season 1/poster.jpg");
  }

  @Test
  public void testSeasonPosterNaming_COVER() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonPosterFilenames();
    settings.addSeasonPosterFilename(TvShowSeasonPosterNaming.COVER);
    settings.getDefaultRenamerProfile().setSpecialSeason(true);

    TvShow show = createShowWithSeasonArtwork("poster_cover", MediaFileType.SEASON_POSTER, "jpg");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "Specials/cover.jpg");
    assertSeasonArtworkExists(show, 1, "Season 1/cover.jpg");
  }

  // =====================================================================================
  // Season Fanart Naming Tests
  // =====================================================================================

  @Test
  public void testSeasonFanartNaming_SEASON_FANART() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonFanartFilenames();
    settings.addSeasonFanartFilename(TvShowSeasonFanartNaming.SEASON_FANART);
    settings.getDefaultRenamerProfile().setSpecialSeason(true);

    TvShow show = createShowWithSeasonArtwork("fanart_season_fanart", MediaFileType.SEASON_FANART, "jpg");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "season-specials-fanart.jpg");
    assertSeasonArtworkExists(show, 1, "season01-fanart.jpg");
  }

  @Test
  public void testSeasonFanartNaming_SEASON_FOLDER() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonFanartFilenames();
    settings.addSeasonFanartFilename(TvShowSeasonFanartNaming.SEASON_FOLDER);
    settings.getDefaultRenamerProfile().setSpecialSeason(true);

    TvShow show = createShowWithSeasonArtwork("fanart_season_folder", MediaFileType.SEASON_FANART, "jpg");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "Specials/season-specials-fanart.jpg");
    assertSeasonArtworkExists(show, 1, "Season 1/season01-fanart.jpg");
  }

  // =====================================================================================
  // Season Banner Naming Tests
  // =====================================================================================

  @Test
  public void testSeasonBannerNaming_SEASON_BANNER() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonBannerFilenames();
    settings.addSeasonBannerFilename(TvShowSeasonBannerNaming.SEASON_BANNER);
    settings.getDefaultRenamerProfile().setSpecialSeason(true);

    TvShow show = createShowWithSeasonArtwork("banner_season_banner", MediaFileType.SEASON_BANNER, "jpg");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "season-specials-banner.jpg");
    assertSeasonArtworkExists(show, 1, "season01-banner.jpg");
  }

  @Test
  public void testSeasonBannerNaming_SEASON_FOLDER() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonBannerFilenames();
    settings.addSeasonBannerFilename(TvShowSeasonBannerNaming.SEASON_FOLDER);
    settings.getDefaultRenamerProfile().setSpecialSeason(true);

    TvShow show = createShowWithSeasonArtwork("banner_season_folder", MediaFileType.SEASON_BANNER, "jpg");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "Specials/season-specials-banner.jpg");
    assertSeasonArtworkExists(show, 1, "Season 1/season01-banner.jpg");
  }

  // =====================================================================================
  // Season Thumb Naming Tests
  // =====================================================================================

  @Test
  public void testSeasonThumbNaming_SEASON_THUMB() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonThumbFilenames();
    settings.addSeasonThumbFilename(TvShowSeasonThumbNaming.SEASON_THUMB);
    settings.getDefaultRenamerProfile().setSpecialSeason(true);

    TvShow show = createShowWithSeasonArtwork("thumb_season_thumb", MediaFileType.SEASON_THUMB, "jpg");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "season-specials-thumb.jpg");
    assertSeasonArtworkExists(show, 1, "season01-thumb.jpg");
  }

  @Test
  public void testSeasonThumbNaming_SEASON_FOLDER() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonThumbFilenames();
    settings.addSeasonThumbFilename(TvShowSeasonThumbNaming.SEASON_FOLDER);
    settings.getDefaultRenamerProfile().setSpecialSeason(true);

    TvShow show = createShowWithSeasonArtwork("thumb_season_folder", MediaFileType.SEASON_THUMB, "jpg");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "Specials/season-specials-thumb.jpg");
    assertSeasonArtworkExists(show, 1, "Season 1/season01-thumb.jpg");
  }

  @Test
  public void testSeasonThumbNaming_SEASON_LANDSCAPE() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonThumbFilenames();
    settings.addSeasonThumbFilename(TvShowSeasonThumbNaming.SEASON_LANDSCAPE);
    settings.getDefaultRenamerProfile().setSpecialSeason(true);

    TvShow show = createShowWithSeasonArtwork("thumb_season_landscape", MediaFileType.SEASON_THUMB, "jpg");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "season-specials-landscape.jpg");
    assertSeasonArtworkExists(show, 1, "season01-landscape.jpg");
  }

  @Test
  public void testSeasonThumbNaming_SEASON_FOLDER_LANDSCAPE() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonThumbFilenames();
    settings.addSeasonThumbFilename(TvShowSeasonThumbNaming.SEASON_FOLDER_LANDSCAPE);
    settings.getDefaultRenamerProfile().setSpecialSeason(true);

    TvShow show = createShowWithSeasonArtwork("thumb_season_folder_landscape", MediaFileType.SEASON_THUMB, "jpg");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "Specials/season-specials-landscape.jpg");
    assertSeasonArtworkExists(show, 1, "Season 1/season01-landscape.jpg");
  }

  // =====================================================================================
  // Season NFO Naming Tests
  // =====================================================================================

  @Test
  public void testSeasonNfoNaming_SEASON() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonNfoFilenames();
    settings.addSeasonNfoFilename(TvShowSeasonNfoNaming.SEASON);

    TvShow show = createShowWithSeasonArtwork("nfo_season", MediaFileType.NFO, "nfo");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "season-specials.nfo");
    assertSeasonArtworkExists(show, 1, "season01.nfo");
  }

  @Test
  public void testSeasonNfoNaming_SEASON_FOLDER() throws Exception {
    TvShowSettings settings = TvShowSettings.getInstance();
    settings.clearSeasonNfoFilenames();
    settings.addSeasonNfoFilename(TvShowSeasonNfoNaming.SEASON_FOLDER);

    TvShow show = createShowWithSeasonArtwork("nfo_season_folder", MediaFileType.NFO, "nfo");
    renameTvShow(show);

    assertSeasonArtworkExists(show, 0, "Specials/season.nfo");
    assertSeasonArtworkExists(show, 1, "Season 1/season.nfo");
  }

  // =====================================================================================
  // Renamer Profile Tests
  // =====================================================================================

  @Test
  public void testRenamerProfileDefaultValues() {
    TvShowRenamerProfile profile = new TvShowRenamerProfile();

    assertThat(profile.getRenamerTvShowFoldername()).isEqualTo(TvShowSettings.DEFAULT_RENAMER_FOLDER_PATTERN);
    assertThat(profile.getRenamerSeasonFoldername()).isEqualTo(TvShowSettings.DEFAULT_RENAMER_SEASON_PATTERN);
    assertThat(profile.getRenamerFilename()).isEqualTo(TvShowSettings.DEFAULT_RENAMER_FILE_PATTERN);
    assertThat(profile.getRenamerMultiEpisodeStyle()).isEqualTo(TvShowMultiEpisodeStyle.REPEAT);
    assertThat(profile.isRenamerShowPathnameSpaceSubstitution()).isFalse();
    assertThat(profile.getRenamerShowPathnameSpaceReplacement()).isEqualTo("_");
    assertThat(profile.isRenamerFilenameSpaceSubstitution()).isFalse();
    assertThat(profile.getRenamerFilenameSpaceReplacement()).isEqualTo("_");
    assertThat(profile.getRenamerColonReplacement()).isEqualTo("");
    assertThat(profile.isRenamerCleanupUnwanted()).isFalse();
    assertThat(profile.getRenamerFirstCharacterNumberReplacement()).isEqualTo("#");
    assertThat(profile.isAsciiReplacement()).isFalse();
    assertThat(profile.isUnicodeReplacement()).isFalse();
    assertThat(profile.isSpecialSeason()).isTrue();
    assertThat(profile.isCreateMissingSeasonItems()).isFalse();
  }

  @Test
  public void testRenamerProfileCustomPatterns() {
    // create a named profile with custom patterns
    TvShowRenamerProfile custom = new TvShowRenamerProfile("CustomPatterns");
    custom.setRenamerTvShowFoldername("TV/${showTitle}");
    custom.setRenamerSeasonFoldername("Staffel ${seasonNr}");
    custom.setRenamerFilename("${showTitle}_S${seasonNr2}E${episodeNr2}_${title}");

    TvShow single = createSingleTvShow();
    Path result = gen(single, custom.getRenamerTvShowFoldername(), custom.getRenamerSeasonFoldername(), custom.getRenamerFilename(), true, custom);

    assertEqual(p("TV/singleshow/Staffel 1/singleshow_S01E02_singleEP.avi"), result);
  }

  @Test
  public void testRenamerProfileMultiEpisodeStyleRepeat() {
    // REPEAT is the default: "S01E02 S01E03"
    TvShowRenamerProfile profile = new TvShowRenamerProfile();
    assertThat(profile.getRenamerMultiEpisodeStyle()).isEqualTo(TvShowMultiEpisodeStyle.REPEAT);

    TvShow multi = createMultiTvShow();
    assertEqual("multishow - S01E02 S01E03 - multiEP2 - multiEP3",
        TvShowRenamer.createDestination("${showTitle} - S${seasonNr2}E${episodeNr2} - ${title}", multi.getEpisodes(), profile));
  }

  @Test
  public void testRenamerProfileMultiEpisodeStyleRange() {
    // RANGE style: "S01E02-E03"
    TvShowRenamerProfile profile = new TvShowRenamerProfile();
    profile.setRenamerMultiEpisodeStyle(TvShowMultiEpisodeStyle.RANGE);

    TvShow multi = createMultiTvShow();
    assertEqual("multishow - S01E02-E03 - multiEP2 - multiEP3",
        TvShowRenamer.createDestination("${showTitle} - S${seasonNr2}E${episodeNr2} - ${title}", multi.getEpisodes(), profile));
  }

  @Test
  public void testRenamerProfileTwoProfilesDifferentOutputs() {
    // two profiles with different show folder patterns should produce different paths
    TvShow single = createSingleTvShow();

    TvShowRenamerProfile profileA = new TvShowRenamerProfile("A");
    profileA.setRenamerTvShowFoldername("${showTitle}");

    TvShowRenamerProfile profileB = new TvShowRenamerProfile("B");
    profileB.setRenamerTvShowFoldername("SHOW ${showYear} ${showTitle}");
    profileB.setRenamerShowPathnameSpaceSubstitution(true);
    profileB.setRenamerShowPathnameSpaceReplacement("_");

    String pathA = TvShowRenamer.getTvShowFoldername(profileA, single);
    String pathB = TvShowRenamer.getTvShowFoldername(profileB, single);

    assertThat(pathA).isEqualTo("singleshow");
    assertThat(pathB).isEqualTo("SHOW_2009_singleshow");
    assertThat(pathA).isNotEqualTo(pathB);
  }

  @Test
  public void testRenamerProfileFromSettingsUsedInRenameTask() throws Exception {
    // add a custom profile to settings and verify it's used by the rename task
    copyResourceFolderToWorkFolder("testtvshows/renamer_test/simple");

    Path source = getWorkFolder().resolve("testtvshows/renamer_test/simple");
    Path destination = getWorkFolder().resolve("tv_show_renamer_profile/ShowForRenamer");
    FileUtils.copyDirectory(source.toFile(), destination.toFile());

    TvShow show = new TvShow();
    show.setTitle("Breaking Bad");
    show.setYear(2008);
    show.setDataSource(destination.getParent().toAbsolutePath().toString());
    show.setPath(destination.toAbsolutePath().toString());

    TvShowList.getInstance().addTvShow(show);

    TvShowEpisode ep = new TvShowEpisode();
    ep.setTitle("Pilot");
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, 1, 1));
    ep.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, 1, 1));
    ep.setPath(destination.toAbsolutePath().toString());
    MediaFile mf = new MediaFile(destination.resolve("S01E01.jpg").toAbsolutePath(), MediaFileType.THUMB);
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01.mkv").toAbsolutePath(), MediaFileType.VIDEO);
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01.srt").toAbsolutePath(), MediaFileType.SUBTITLE);
    ep.addToMediaFiles(mf);
    mf = new MediaFile(destination.resolve("S01E01.nfo").toAbsolutePath(), MediaFileType.NFO);
    ep.addToMediaFiles(mf);
    ep.setTvShow(show);
    show.addEpisode(ep);

    // add a custom profile with known patterns
    TvShowSettings settings = TvShowSettings.getInstance();
    TvShowRenamerProfile customProfile = new TvShowRenamerProfile("RenameTestProfile");
    customProfile.setRenamerTvShowFoldername("${showTitle} (${showYear})");
    customProfile.setRenamerSeasonFoldername("Season ${seasonNr}");
    customProfile.setRenamerFilename("${showTitle} - S${seasonNr2}E${episodeNr2} - ${title}");
    settings.addRenamerProfile(customProfile);

    // run rename with the custom profile
    TvShowRenameTask task = new TvShowRenameTask(Collections.singletonList(show), show.getEpisodes(), customProfile);
    task.run();

    // verify files were renamed using the custom profile's patterns
    Path expectedShowDir = destination.getParent().resolve("Breaking Bad (2008)");
    Path expectedFile = expectedShowDir.resolve("Season 1/Breaking Bad - S01E01 - Pilot.mkv");
    assertThat(expectedFile).exists();

    // now use another setting and rename again
    customProfile.setRenamerShowPathnameSpaceSubstitution(true);
    customProfile.setRenamerShowPathnameSpaceReplacement("_");
    customProfile.setRenamerSeasonFoldername("S${seasonNr2}");

    task = new TvShowRenameTask(Collections.singletonList(show), show.getEpisodes(), customProfile);
    task.run();

    // verify files were renamed using the custom profile's patterns
    expectedShowDir = destination.getParent().resolve("Breaking_Bad_(2008)");
    expectedFile = expectedShowDir.resolve("S01/Breaking Bad - S01E01 - Pilot.mkv");
    assertThat(expectedFile).exists();
  }
}
