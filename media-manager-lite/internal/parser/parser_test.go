package parser

import (
	"reflect"
	"testing"
)

// Sample filenames spanning English, Chinese, Japanese and Korean titles,
// technical tag combinations and edge cases.
func TestParse(t *testing.T) {
	cases := []struct {
		name string
		in   string
		want Parsed
	}{
		// --- English basics ---
		{"plain title+year+tech", "Movie.Name.2025.1080p.BluRay.x264.mkv",
			Parsed{Title: "Movie Name", Year: 2025, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"title with the", "The.Matrix.1999.720p.BluRay.x264.mkv",
			Parsed{Title: "The Matrix", Year: 1999, Resolution: "720p", Source: "BluRay", VideoCodec: "x264"}},
		{"web-dl ddp h264", "Movie.2019.1080p.WEB-DL.DDP5.1.H.264-CM.mkv",
			Parsed{Title: "Movie", Year: 2019, Resolution: "1080p", Source: "WEB-DL", VideoCodec: "H.264", Audio: []string{"DD+"}}},
		{"webrip aac", "Film.2020.1080p.WEBRip.x264.AAC5.1.mkv",
			Parsed{Title: "Film", Year: 2020, Resolution: "1080p", Source: "WEBRip", VideoCodec: "x264", Audio: []string{"AAC"}}},
		{"hdtv source", "Show.2021.1080p.HDTV.x264.mkv",
			Parsed{Title: "Show", Year: 2021, Resolution: "1080p", Source: "HDTV", VideoCodec: "x264"}},
		{"dvdrip xvid", "Old.1998.DVDRip.XviD.avi",
			Parsed{Title: "Old", Year: 1998, Source: "DVDRip", VideoCodec: "XviD"}},
		{"extended edition still cut at year", "Avatar.2009.Extended.Collectors.Edition.1080p.BluRay.DTS-HD.MA.5.1.mkv",
			Parsed{Title: "Avatar", Year: 2009, Resolution: "1080p", Source: "BluRay", Audio: []string{"DTS-HD MA"}}},
		{"blu-ray spelled with hyphen", "UPPER.CASE.2010.1080p.BLU-RAY.X264.mkv",
			Parsed{Title: "UPPER CASE", Year: 2010, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"4k alias", "Movie.2023.4K.HDR.HEVC.mkv",
			Parsed{Title: "Movie", Year: 2023, Resolution: "2160p", HDR: "HDR", VideoCodec: "HEVC"}},
		{"remux source", "Movie.2018.2160p.Remux.HEVC.TrueHD.mkv",
			Parsed{Title: "Movie", Year: 2018, Resolution: "2160p", Source: "Remux", VideoCodec: "HEVC", Audio: []string{"TrueHD"}}},

		// --- HDR / DV ---
		{"dolby vision dovi", "Dune.Part.One.2021.2160p.BluRay.DoVi.HDR10.HEVC.Atmos.TrueHD.7.1.mkv",
			Parsed{Title: "Dune Part One", Year: 2021, Resolution: "2160p", Source: "BluRay",
				VideoCodec: "HEVC", HDR: "Dolby Vision", Audio: []string{"Atmos", "TrueHD"}}},
		{"hdr10plus", "Movie.2022.2160p.WEB-DL.HDR10+.HEVC.mkv",
			Parsed{Title: "Movie", Year: 2022, Resolution: "2160p", Source: "WEB-DL", HDR: "HDR10+", VideoCodec: "HEVC"}},
		{"dv then hdr10", "Movie.2022.2160p.WEB-DL.DV.HDR10.HEVC.Atmos.mkv",
			Parsed{Title: "Movie", Year: 2022, Resolution: "2160p", Source: "WEB-DL",
				HDR: "Dolby Vision", VideoCodec: "HEVC", Audio: []string{"Atmos"}}},

		// --- Year disambiguation ---
		{"title is 1917 with real year", "1917.2019.1080p.BluRay.x264.mkv",
			Parsed{Title: "1917", Year: 2019, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"title 2012 with real year", "2012.2009.1080p.BluRay.x264.mkv",
			Parsed{Title: "2012", Year: 2009, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"title 2012 without year", "2012.1080p.BluRay.x264.mkv",
			Parsed{Title: "2012", Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"blade runner 2049 with year", "Blade.Runner.2049.2017.1080p.BluRay.x264.mkv",
			Parsed{Title: "Blade Runner 2049", Year: 2017, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"blade runner 2049 without year", "Blade.Runner.2049.1080p.BluRay.x264.mkv",
			Parsed{Title: "Blade Runner 2049", Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"wonder woman 1984", "Wonder.Woman.1984.2020.2160p.BluRay.HEVC.mkv",
			Parsed{Title: "Wonder Woman 1984", Year: 2020, Resolution: "2160p", Source: "BluRay", VideoCodec: "HEVC"}},
		{"2001 a space odyssey", "Movie.2001.A.Space.Odyssey.1968.1080p.BluRay.x264.mkv",
			Parsed{Title: "Movie 2001 A Space Odyssey", Year: 1968, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"title only number 300", "300.2006.1080p.BluRay.x264.mkv",
			Parsed{Title: "300", Year: 2006, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"title 1917 no year given", "1917.1080p.BluRay.x264.mkv",
			Parsed{Title: "1917", Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"se7en", "Se7en.1995.720p.BluRay.x264.mkv",
			Parsed{Title: "Se7en", Year: 1995, Resolution: "720p", Source: "BluRay", VideoCodec: "x264"}},
		{"42 title", "42.2013.1080p.BluRay.x264.mkv",
			Parsed{Title: "42", Year: 2013, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"gone in 60 seconds", "Gone.in.60.Seconds.2000.720p.BluRay.x264.mkv",
			Parsed{Title: "Gone in 60 Seconds", Year: 2000, Resolution: "720p", Source: "BluRay", VideoCodec: "x264"}},
		{"terminator 2", "Terminator.2.Judgment.Day.1991.1080p.BluRay.x265.mkv",
			Parsed{Title: "Terminator 2 Judgment Day", Year: 1991, Resolution: "1080p", Source: "BluRay", VideoCodec: "x265"}},
		{"year alone with title", "Movie.2019.mkv", Parsed{Title: "Movie", Year: 2019}},
		{"no year at all", "Movie.Name.1080p.BluRay.x264.mkv",
			Parsed{Title: "Movie Name", Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},

		// --- CD / Part / Disc markers ---
		{"cd1", "Movie.CD1.avi", Parsed{Title: "Movie", Part: "CD1"}},
		{"cd2 with year", "Movie.2010.CD2.avi", Parsed{Title: "Movie", Year: 2010, Part: "CD2"}},
		{"disc 2", "Movie.2019.Disc.2.mkv", Parsed{Title: "Movie", Year: 2019, Part: "DISC2"}},
		{"part 1 before year", "Movie.Part.1.2009.mkv", Parsed{Title: "Movie", Part: "PART1"}},
		{"stacked cd", "Double.Feature.2015.720p.BluRay.x264.CD1.avidx",
			Parsed{Title: "Double Feature", Year: 2015, Resolution: "720p", Source: "BluRay", VideoCodec: "x264", Part: "CD1"}},

		// --- CJK titles ---
		{"chinese title", "你的名字.2016.1080p.BluRay.x264.mkv",
			Parsed{Title: "你的名字", Year: 2016, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"japanese title", "君の名は.2016.1080p.BluRay.x264.mkv",
			Parsed{Title: "君の名は", Year: 2016, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"korean title", "기생충.2019.2160p.BluRay.HEVC.mkv",
			Parsed{Title: "기생충", Year: 2019, Resolution: "2160p", Source: "BluRay", VideoCodec: "HEVC"}},
		{"chinese long title web-dl", "鬼灭之刃剧场版.无限列车篇.2020.1080p.WEB-DL.x264.mkv",
			Parsed{Title: "鬼灭之刃剧场版 无限列车篇", Year: 2020, Resolution: "1080p", Source: "WEB-DL", VideoCodec: "x264"}},
		{"mixed english chinese", "Your.Name.你的名字.2016.BluRay.x264.mkv",
			Parsed{Title: "Your Name 你的名字", Year: 2016, Source: "BluRay", VideoCodec: "x264"}},
		{"chinese with aac", "沉静如海.2004.1080p.WEB-DL.AAC.H.264.mkv",
			Parsed{Title: "沉静如海", Year: 2004, Resolution: "1080p", Source: "WEB-DL", VideoCodec: "H.264", Audio: []string{"AAC"}}},

		// --- Special characters / shapes ---
		{"hyphen kept in title", "Spider-Man.Homecoming.2017.1080p.BluRay.x264.mkv",
			Parsed{Title: "Spider-Man Homecoming", Year: 2017, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"apostrophe kept", "Ocean's.Eleven.2001.1080p.BluRay.x264.mkv",
			Parsed{Title: "Ocean's Eleven", Year: 2001, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"parenthesized year", "Movie.(2019).1080p.BluRay.x264.mkv",
			Parsed{Title: "Movie", Year: 2019, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"underscores as separators", "Movie_Name_2020_1080p_BluRay_x264.mkv",
			Parsed{Title: "Movie Name", Year: 2020, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"spaces in name", "A Movie (2021) [1080p].mkv",
			Parsed{Title: "A Movie", Year: 2021, Resolution: "1080p"}},
		{"no metadata at all", "PlainMovie.mkv", Parsed{Title: "PlainMovie"}},
		{"group tag after codec", "Mission.Impossible.Ghost.Protocol.2011.1080p.BluRay.X264-Japhson.mkv",
			Parsed{Title: "Mission Impossible Ghost Protocol", Year: 2011, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"stream details multi audio", "Deadpool.2016.1080p.BluRay.DTS-HD.MA.7.1.Atmos.TrueHD.x264.mkv",
			Parsed{Title: "Deadpool", Year: 2016, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264",
				Audio: []string{"DTS-HD MA", "Atmos", "TrueHD"}}},
		{"amzn tag ignored in title", "Knives.Out.2019.1080p.AMZN.WEB-DL.DDP5.1.H.264.mkv",
			Parsed{Title: "Knives Out", Year: 2019, Resolution: "1080p", Source: "WEB-DL", VideoCodec: "H.264", Audio: []string{"DD+"}}},
		{"avc alias", "Children.of.Men.2006.1080p.BluRay.H264.AC3.mkv",
			Parsed{Title: "Children of Men", Year: 2006, Resolution: "1080p", Source: "BluRay", VideoCodec: "H.264", Audio: []string{"AC3"}}},
		{"source only no year", "Movie.DVDRip.avi", Parsed{Title: "Movie", Source: "DVDRip"}},
		{"roman numerals", "Star.Wars.Episode.IV.A.New.Hope.1977.1080p.BluRay.x264.mkv",
			Parsed{Title: "Star Wars Episode IV A New Hope", Year: 1977, Resolution: "1080p", Source: "BluRay", VideoCodec: "x264"}},
		{"av1 codec", "Movie.2024.2160p.WEB-DL.AV1.Opus.mkv",
			Parsed{Title: "Movie", Year: 2024, Resolution: "2160p", Source: "WEB-DL", VideoCodec: "AV1", Audio: []string{"Opus"}}},
		{"mp3 legacy audio", "Classic.1985.DVDRip.XviD.MP3.avi",
			Parsed{Title: "Classic", Year: 1985, Source: "DVDRip", VideoCodec: "XviD", Audio: []string{"MP3"}}},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := Parse(tc.in)
			if !reflect.DeepEqual(got, tc.want) {
				t.Errorf("Parse(%q)\n got %+v\nwant %+v", tc.in, got, tc.want)
			}
			if got.Title == "" {
				t.Errorf("Parse(%q) returned an empty title", tc.in)
			}
		})
	}
}

func TestIsVideoFile(t *testing.T) {
	positive := []string{
		"movie.mkv", "movie.MKV", "a/b/movie.mp4", "movie.m2ts", "movie.iso",
		"movie.vob", "movie.webm", "movie.AVI", "x.m4v", "y.mts",
	}
	for _, in := range positive {
		if !IsVideoFile(in) {
			t.Errorf("IsVideoFile(%q) = false, want true", in)
		}
	}

	negative := []string{
		"movie.nfo", "poster.jpg", "fanart.png", "subs.eng.srt", "subs.ass",
		"cover.jpeg", "logo.gif", "notes.txt", "movie", "movie.mkv.nfo",
		"banner.bmp", "index.html", "audio.flac", "data.xml",
	}
	for _, in := range negative {
		if IsVideoFile(in) {
			t.Errorf("IsVideoFile(%q) = true, want false", in)
		}
	}
}

func TestIsTrailerOrSample(t *testing.T) {
	skipped := []string{
		"sample.mkv", "Sample.avi", "sample-trailer.mkv", "trailer.mp4",
		"The.Matrix.1999.sample.mkv", "Movie.trailer.mkv", "sample.2.mkv",
	}
	for _, in := range skipped {
		if !IsTrailerOrSample(in) {
			t.Errorf("IsTrailerOrSample(%q) = false, want true", in)
		}
	}

	kept := []string{
		"Trailer.Park.Boys.2001.mkv", // real title starting with "Trailer."
		"Movie.2019.1080p.BluRay.x264.mkv",
		"Samples.of.Time.2020.mkv", // "Samples" is not the exact marker
	}
	for _, in := range kept {
		if IsTrailerOrSample(in) {
			t.Errorf("IsTrailerOrSample(%q) = true, want false", in)
		}
	}
}
