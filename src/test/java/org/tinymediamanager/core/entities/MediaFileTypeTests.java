package org.tinymediamanager.core.entities;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.tinymediamanager.core.MediaFileType;

public class MediaFileTypeTests {

  @Test
  public void testPositivePosters() {
    List<String> positives = Arrays.asList("poster.jpg", // exact keyword + simple ext
        "Poster.JPG", // case-insensitive name + ext
        "movie.PNG", // different keyword + ext (upper ext)
        "folder.tbn", // different allowed ext
        "cover.webp", // webp extension
        "my.poster.png", // dot separator before keyword
        "my-poster.gif", // dash separator before keyword
        "my_poster.jpeg", // underscore separator and jpeg ext
        "long.name.with.many-separators-movie.png", // multiple separators in prefix
        "file name_with spaces_poster.jpg", // spaces allowed in prefix characters
        "file name_with spaces and brackets (2006) [x264]_poster.jpg", // brackets allowed in prefix characters
        "file with ÄÜÖ-poster.jpg", // umlauts are allowed
        "movie'-poster.jpg", // apostrophe in prefix (not covered by \\w)
        "@buelos-poster.jpg", // at-sign in prefix (not covered by \\w)
        "movie!_poster.png", // exclamation mark in prefix (not covered by \\w)
        "movie😀-poster.jpg", // emoji in prefix (not covered by \\w)
        "电影-poster.jpg", // CJK letters in prefix (not covered by default ASCII \\w)
        ".45-poster.webp" // starting with a dot
    );
    for (String name : positives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertTrue("Expected to match POSTER, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.POSTER);
    }
  }

  @Test
  public void testNegativePosters() {
    List<String> negatives = Arrays.asList("aposter.jpg", // no separator before keyword
        "poster1.jpg", // extra character appended to keyword
        "poster.jpegx", // invalid extension (close but not listed)
        "poster.jp", // too-short extension
        "posterjpg", // missing dot before extension
        "poster..jpg", // extra dot breaks extension match
        "my poster.jpg", // space immediately before keyword (space not accepted as separator)
        ".poster.jpg", // leading dot without preceding token+separator
        "poster.jpg ", // trailing space
        "poster.JPGX" // invalid extension variant (case-insensitive checked but ext not allowed)
    );
    for (String name : negatives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertFalse("Expected NOT to match POSTER, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.POSTER);
    }
  }

  @Test
  public void testPositiveFanarts() {
    List<String> positives = Arrays.asList("backdrop.jpg", // simple, token only
        "fanart.png", // simple, different token and ext
        "background.jpeg", // alternative extension
        "my_backdrop.jpg", // prefix with underscore separator
        "cover.backdrop.jpg", // prefix with dot separator
        "pre.v1-backdrop.webp", // prefix with dots, digits and hyphen
        "a-b_fanart.tbn", // combined hyphen and underscore in prefix, tbn ext
        "012_background.GIF", // numeric prefix and uppercase extension (case-insensitive)
        "BACKDROP.JpG", // token and extension mixed-case (case-insensitive)
        "file with ÄÜÖ-fanart.jpg", // umlauts are allowed
        "show'-backdrop.jpg", // apostrophe in prefix (not covered by \\w)
        "@buelos_fanart.png", // at-sign in prefix (not covered by \\w)
        "show!_background.jpeg", // exclamation mark in prefix (not covered by \\w)
        "show😀-fanart.webp", // emoji in prefix (not covered by \\w)
        "影片-background.jpg", // CJK letters in prefix (not covered by default ASCII \\w)
        "file name_with spaces and brackets (2006) [x264]-fanart.jpg" // brackets allowed in prefix characters
    );
    for (String name : positives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertTrue("Expected to match FANART, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.FANART);
    }
  }

  @Test
  public void testNegativeFanarts() {
    List<String> negatives = Arrays.asList("notbackdrop.jpg", // token as substring without separator
        "backdropx.jpg", // extra character attached to token
        "imagebackdrop.jpg", // token embedded in a larger word
        "fanartpng", // missing dot before extension
        "fan.art.jpg", // token split by separator (not exact token)
        ".backdrop.jpg", // leading dot but no valid prefix+separator before token
        "backdrop.jpgg", // invalid/unknown extension
        "backdrop.jpg.zip", // extra extension after allowed one
        "backgroundbmp", // missing dot and extension
        "backdrop.", // trailing dot but missing extension
        "BACKDROPP.jpg" // misspelled token
    );
    for (String name : negatives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertFalse("Expected NOT to match FANART, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.FANART);
    }
  }

  @Test
  public void testPositiveExtraFanarts() {
    List<String> positives = Arrays.asList(
        // minimal: keyword + digits + ext
        "fanart1.jpg",
        // mixed case keyword and upper-case extension (case-insensitive)
        "FanArt001.PNG",
        // multiple prefix segments separated by dot/underscore/dash
        "show.name_s01-fanart10.png",
        // keyword 'backdrop' with digits and tbn ext
        "my.backdrop123.tbn",
        // 'background' with zero digit (0) accepted
        "background0.gif",
        // dash before keyword and long digits + webp
        "poster-backdrop999.webp",
        // bmp extension uppercase
        "background123.BMP",
        // underscores and dots in prefix, multiple segments
        "series_s01.v2-backdrop42.jpeg",
        // many digits
        "fanart0000000000002.jpg");
    for (String name : positives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertTrue("Expected to match EXTRAFANART, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.EXTRAFANART);
    }
  }

  @Test
  public void testNegativeExtraFanarts() {
    List<String> negatives = Arrays.asList(
        // missing digits after keyword
        "fanart.jpg",
        // non-listed extension
        "fanart12.txt",
        // separator between keyword and digits (digits must be immediately after keyword)
        "fanart-10.jpg",
        // keyword occurs as suffix without required separator before it
        "prefanart1.jpg",
        // keyword embedded without separator before it
        "somethingbackground5.gif",
        // extra characters between keyword and digits
        "myfanart_12.jpg",
        // missing dot before extension
        "backdrop5jpg",
        // extension typo
        "background12.jpegx",
        // leading/trailing spaces inside filename (space not handled as separator before keyword)
        "movie fanart12.png",
        // no keyword at all
        "poster_01.jpg");
    for (String name : negatives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertFalse("Expected NOT to match EXTRAFANART, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.EXTRAFANART);
    }
  }

  @Test
  public void testPositiveBanner() {
    List<String> positives = Arrays.asList("banner.jpg", // exact name + lower-case ext
        "BaNnEr.JpG", // case-insensitive match for name+ext
        "site_banner.png", // single prefix + underscore separator
        "01.banner.gif", // numeric prefix + dot separator
        "multi.part-name_banner.webp", // mixed separators and prefix segments
        "a-banner.tbn", // hyphen separator
        "long.name.with.dots.banner.bmp", // multiple dots before banner
        "my_banner.jpeg", // underscore within prefix
        "banner.TBN", // extension case-insensitive (tbn)
        "prefix.with-dashes_and.under.banner.PNG" // many separators + mixed case ext
    );
    for (String name : positives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertTrue("Expected to match BANNER, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.BANNER);
    }
  }

  @Test
  public void testNegativeBanner() {
    List<String> negatives = Arrays.asList("banner.jpg.txt", // extra trailing extension -> should not match
        "xbanner.jpg", // 'banner' is substring, not preceded by separator or start
        "bannerjpg", // missing dot before extension
        "banner.", // dot but missing extension
        "_banner.jpg", // starts with separator without a prefix token -> does not fit group
        "banner.jpgg", // invalid extension (not in allowed list)
        "pre banner.jpg", // space before 'banner' instead of required separator
        "banner..jpg", // double dot between name and extension -> invalid
        "bannerjpeg", // extension concatenated, no dot
        "ban-ner.jpg" // 'banner' must be exact token, this splits it -> should not match
    );
    for (String name : negatives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertFalse("Expected NOT to match BANNER, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.BANNER);
    }
  }

  @Test
  public void testPositiveThumb() {
    List<String> positives = Arrays.asList(
        // minimal keyword, no digits
        "thumb.jpg",
        // case-insensitive keyword and extension
        "Thumb.JPG",
        // other keyword
        "landscape.png",
        // prefix with underscore and two digits (boundary: max 2 digits)
        "my_thumb01.jpeg",
        // prefix with dot and hyphen then two digits (complex prefix)
        "my.photo-landscape99.webp",
        // single digit after keyword
        "a-b.c_d-thumb0.gif",
        // single digit zero
        "thumb0.bmp",
        // two digits with 'tbn' extension
        "landscape12.tbn",
        // multiple prefix parts separated by dot/hyphen
        "prefix-with.multiple.parts-landscape.webp",
        // uppercase extension and digits
        "x_thumb98.JPG");
    for (String name : positives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertTrue("Expected to match THUMB, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.THUMB);
    }
  }

  @Test
  public void testNegativeThumb() {
    List<String> negatives = Arrays.asList(
        // too many digits (3)
        "thumb100.jpg",
        // letter after keyword instead of digits
        "thumba.jpg",
        // too many digits after keyword
        "landscape123.png",
        // extra separator between keyword and digits (should be digits immediately)
        "foo_thumb_01.jpg",
        // missing separator before keyword (keyword must be a separated segment or at start)
        "prefixthumb01.jpg",
        // space in extension area / malformed dot
        "thumb. jpg",
        // missing dot before extension
        "thumbpng",
        // extension appended with extra digits (malformed)
        "thumb.jpeg200",
        // invalid extension
        "thumb.jpgg",
        // leading dot before keyword (does not satisfy prefix pattern)
        ".thumb.jpg",
        // no extension at all
        "thumb");
    for (String name : negatives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertFalse("Expected NOT to match THUMB, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.THUMB);
    }
  }

  @Test
  public void testPositiveLogo() {
    List<String> positives = Arrays.asList("logo.png", // simplest: exact 'logo' + allowed extension
        "clearlogo.jpg", // simplest: exact 'clearlogo' + allowed extension
        "Logo.PNG", // case-insensitive name and extension
        "CLEARLOGO.WebP", // case-insensitive clearlogo + webp
        "my_logo.jpeg", // underscore separator before 'logo'
        "my-logo.gif", // hyphen separator before 'logo'
        "my.logo.tbn", // dot separator before 'logo'
        "project.v1-logo.bmp", // complex prefix containing dots/numbers then hyphen separator
        "a_b.c-d.logo.webp", // multiple segments; last segment separator before final 'logo'
        "space in name-logo.gif", // spaces allowed in prefix and hyphen separator before 'logo'
        "a..-logo.png", // multiple dots and hyphen in prefix, ends with separator
        "clearlogo.tbn", // alternative extension 'tbn'
        "logo.jpeg" // 'jpeg' extension

    );
    for (String name : positives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertTrue("Expected to match CLEARLOGO, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.CLEARLOGO);
    }
  }

  @Test
  public void testNegativeLogo() {
    List<String> negatives = Arrays.asList("abclogo.png", // no separator before 'logo' (prefix attached directly) -> should NOT match
        "prelogo.jpg", // prefix immediately before 'logo' with no separator -> NOT match
        "logo", // missing extension -> NOT match
        "logo.pngx", // invalid extension -> NOT match
        "logo..png", // extra dot before extension makes final name not exactly 'logo' -> NOT match
        "logo .png", // space before dot; final name not exactly 'logo' -> NOT match
        "logo.PNG ", // trailing space -> NOT match
        "logojpeg", // missing dot before extension -> NOT match
        "logo..jpeg", // extra dot before extension -> NOT match
        "_logo.gif", // single underscore as prefix (prefix ends with separator)
        ".logo.png"// single dot as prefix (hidden-file style prefix) allowed
    );
    for (String name : negatives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertFalse("Expected NOT to match CLEARLOGO, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.CLEARLOGO);
    }
  }

  @Test
  public void testPositiveCharacterart() {
    List<String> positives = Arrays.asList(
        // no prefix, no digits, common ext
        "characterart.jpg",
        // no prefix, single digit
        "characterart1.png",
        // no prefix, two digits
        "characterart99.webp",
        // case-insensitive name and extension
        "CharacterArt.JPG",
        // simple prefix ending with hyphen
        "prefix-characterart.png",
        // prefix with underscore and dot and ends with separator before name; two digits; jpeg ext
        "multi.part-prefix_characterart02.jpeg",
        // prefix with mixed allowed chars, two zeros, tbn ext
        "a_b.-characterart00.tbn",
        // single-char prefix with separator
        "x-characterart.gif",
        // space allowed in prefix (space is included in the bracketed class)
        "my art_-characterart.bmp");
    for (String name : positives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertTrue("Expected to match CHARACTERART, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.CHARACTERART);
    }
  }

  @Test
  public void testNegativeCharacterart() {
    List<String> negatives = Arrays.asList(
        // more than two digits after 'characterart'
        "characterart123.jpg",
        // letters immediately before 'characterart' but no separator (prefix must end with _ . or -)
        "abccharacterart.jpg",
        // missing dot before extension
        "characterartjpg",
        // invalid extension (not in allowed list)
        "characterart.jpgx",
        // extra '.' between name and digits (digits must be directly after 'characterart')
        "characterart.01.jpg",
        // underscore immediately after name before dot (only digits or nothing allowed between name and dot)
        "characterart_.jpg",
        // wrong base name (typo)
        "charactrart.jpg",
        // extension present but additional trailing chars (must end right after extension)
        "characterart.jpeg200");
    for (String name : negatives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertFalse("Expected NOT to match CHARACTERART, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.CHARACTERART);
    }
  }

  @Test
  public void testPositiveDiscart() {
    List<String> positives = Arrays.asList(
        // no prefix, short base
        "disc.jpg",
        // 'discart' variant
        "discart.png",
        // case-insensitive base + extension
        "DISC.JPG",
        // single prefix segment ending with underscore
        "my_discart.jpeg",
        // prefix contains dot and hyphen before base
        "file.name-disc.gif",
        // prefix contains space and dot separator before base
        "file name.disc.bmp",
        // multiple prefix segments with mixed separators
        "a.b_c-d.discart.webp",
        // numeric prefix and uppercase extension (TBN)
        "123_disc.TBN",
        // long chain of prefix segments
        "long-name.with.many.parts-discart.jpg",
        // prefix begins with underscore and dot separator before base
        "_.disc.jpg");
    for (String name : positives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertTrue("Expected to match DISC, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.DISC);
    }
  }

  @Test
  public void testNegativeDiscart() {
    List<String> negatives = Arrays.asList(
        // misspelled base (should be 'disc' or 'discart' only)
        "discard.jpg",
        // wrong extension
        "disc.txt",
        // missing dot before extension
        "discpng",
        // concatenated prefix without required separator before 'disc'
        "mydisc.jpg",
        // extra suffix after valid extension
        "disc.jpg.bak",
        // leading dot before base (no characters before the separator)
        ".disc.jpg",
        // missing extension entirely
        "disc.",
        // double dot between base and extension (extra char)
        "discart..jpg",
        // extension slightly misspelled
        "disc.jpegx");
    for (String name : negatives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertFalse("Expected NOT to match DISC, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.DISC);
    }
  }

  @Test
  public void testPositiveClearart() {
    List<String> positives = Arrays.asList(
        // direct base case: exact name + allowed extension
        "clearart.jpg",
        // case-insensitive filename + extension
        "CLEARART.GIF",
        // uppercase extension
        "clearart.JPEG",
        // single underscore separator before clearart
        "poster_clearart.png",
        // dot separator before clearart
        "hd.clearart.webp",
        // multiple prefixes with hyphen and dot and underscore before clearart
        "front-box.back_clearart.tbn",
        // digits allowed in prefix
        "123_clearart.bmp",
        // mixed separators in prefixes (dot then hyphen) before clearart
        "pre.fixed-clearart.jpg");
    for (String name : positives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertTrue("Expected to match CLEARART, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.CLEARART);
    }
  }

  @Test
  public void testNegativeClearart() {
    List<String> negatives = Arrays.asList(
        // no separator before clearart (prefix directly adjacent) -> should not match
        "myclearart.jpg",
        // extra characters appended to clearart -> should not match
        "clearartx.jpg",
        // missing dot before extension -> should not match
        "clearartjpg",
        // invalid extension (not in allowed list)
        "clearart.bmp2",
        // space used as separator immediately before clearart (space is not an allowed separator)
        "poster clearart.png",
        // invalid character '+' in prefix (not allowed in [\\w _.-])
        "pre+clearart.jpg",
        // extra trailing dot after extension -> should not match
        "clearart.jpg.",
        // double extension where final extension is not allowed -> should not match
        "poster_clearart.PNG.exe",
        // leading space before clearart without a valid separator -> should not match
        " clearart.jpg");
    for (String name : negatives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertFalse("Expected NOT to match CLEARART, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.CLEARART);
    }
  }

  @Test
  public void testPositiveKeyart() {
    List<String> positives = Arrays.asList("keyart.jpg", // no prefix, simple lowercase
        "KEYART.PNG", // uppercase keyart + extension (case-insensitive)
        "foo_keyart.jpeg", // underscore separator before keyart
        "foo-keyart.gif", // hyphen separator before keyart
        "foo.keyart.bmp", // dot separator before keyart
        "multi.part-name_keyart.webp", // multiple dots/hyphens in prefix
        "with space_keyart.tbn", // space allowed in prefix
        "123_keyart.jpg", // numeric prefix
        "a.-_keyart.gif" // complex prefix ending with underscore
    );
    for (String name : positives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertTrue("Expected to match KEYART, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.KEYART);
    }
  }

  @Test
  public void testNegativeKeyart() {
    List<String> negatives = Arrays.asList("prefixkeyart.jpg", // missing separator before 'keyart'
        "keyart.jpga", // invalid extension
        "mykeyartpng", // no dot before extension / invalid format
        "keyart", // missing extension
        "notkeyart.jpg", // different base name
        "foo.keyarth.jpg", // 'keyarth' != 'keyart'
        ".keyart.jpg", // leading dot before 'keyart' (does not match start)
        "KEYART.jpegx", // wrong extension suffix
        " keyart.jpg", // leading space (no valid prefix separator)
        "keyart.jpg " // trailing space after extension
    );
    for (String name : negatives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertFalse("Expected NOT to match KEYART, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.KEYART);
    }
  }

  @Test
  public void testPositiveSquareart() {
    List<String> positives = Arrays.asList(
        // direct base cases (square, squareart, backgroundSquare) + allowed extensions
        "squareart.jpg", "square.png", "backgroundSquare.jpg",
        // case-insensitive
        "SQUAREART.PNG", "Square.JpG", "BACKGROUNDSQUARE.gif",
        // single separator before keyword
        "foo_squareart.jpeg", "foo-squareart.webp", "foo.squareart.bmp", "foo.square.tbn", "foo.backgroundSquare.jpg",
        // multiple prefix segments with mixed separators
        "multi.part-name_squareart.png", "with space_squareart.jpg", "123_squareart.gif");
    for (String name : positives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertTrue("Expected to match SQUAREART, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.SQUAREART);
    }
  }

  @Test
  public void testNegativeSquareart() {
    List<String> negatives = Arrays.asList(
        // missing separator before keyword
        "mysquareart.jpg",
        // extra character appended to keyword
        "squareartx.jpg",
        // missing dot before extension
        "squareartjpg",
        // invalid extension
        "squareart.txt",
        // leading dot before keyword (no valid prefix+separator)
        ".square.jpg",
        // trailing space after extension
        "square.jpg ",
        // space used as separator immediately before keyword
        "pre square.jpg",
        // missing extension
        "backgroundSquare");
    for (String name : negatives) {
      MediaFile mf = new MediaFile(Path.of(name));
      Assert.assertFalse("Expected NOT to match SQUAREART, but was " + mf.getType() + ": " + name, mf.getType() == MediaFileType.SQUAREART);
    }
  }
}
