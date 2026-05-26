package main

import (
	"encoding/xml"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"strconv"
	"strings"
)

const (
	WAV_DIR          = `B:\Software\Fmod_Bank_Tools\wav`
	XML_PATH         = `A:\Game\ForzaHorizon6\media\Audio\RadioInfo_CN.xml`
	WAV_HEADER_SIZE  = 46
	BYTES_PER_SAMPLE = 4 // 16-bit stereo = 4 bytes per sample point
)

var soundNameRe = regexp.MustCompile(`^(HZ\d+_R\d+)_(.+)$`)

type TrackSample struct {
	SoundName    string
	SampleLength int
}

type WavSample struct {
	Bank      string
	Index     int
	SamplePts int // (FileSize - WAV_HEADER_SIZE) / BYTES_PER_SAMPLE
}

type matchResult struct {
	suffix     string
	bank       string
	index      int
	prefix     string
	station    string
	warning    string
	candidates []matchResult
}

// splitSoundName 将 "HZ6_R1_BAYNK_Grin" 拆为 "HZ6_R1" + "BAYNK_Grin"
func splitSoundName(s string) (prefix, suffix string) {
	m := soundNameRe.FindStringSubmatch(s)
	if m == nil {
		return "", s
	}
	return m[1], m[2]
}

// map[StationName][]TrackSample
func parseXML(path string) (map[string][]TrackSample, map[string]int) {
	f, err := os.Open(path)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: cannot open XML: %v\n", err)
		os.Exit(1)
	}
	defer f.Close()

	dec := xml.NewDecoder(f)
	result := make(map[string][]TrackSample)
	numberByName := make(map[string]int)
	var currentStation string
	var inTrackList bool

	for {
		tok, err := dec.Token()
		if err == io.EOF {
			break
		}
		if err != nil {
			fmt.Fprintf(os.Stderr, "ERROR: XML token error: %v\n", err)
			os.Exit(1)
		}

		switch t := tok.(type) {
		case xml.StartElement:
			if t.Name.Local == "RadioStation" {
				for _, attr := range t.Attr {
					switch attr.Name.Local {
					case "Name":
						currentStation = attr.Value
					case "Number":
						n, _ := strconv.Atoi(attr.Value)
						numberByName[currentStation] = n
					}
				}
			}
			if t.Name.Local == "SampleList" {
				for _, attr := range t.Attr {
					if attr.Name.Local == "Type" && attr.Value == "Track" {
						inTrackList = true
					}
				}
			}
			if t.Name.Local == "Sample" && inTrackList && currentStation != "" {
				sample := TrackSample{}
				for _, attr := range t.Attr {
					switch attr.Name.Local {
					case "SoundName":
						sample.SoundName = attr.Value
					case "SampleLength":
						sample.SampleLength, _ = strconv.Atoi(attr.Value)
					}
				}
				if sample.SoundName != "" {
					result[currentStation] = append(result[currentStation], sample)
				}
			}
		case xml.EndElement:
			if t.Name.Local == "SampleList" {
				inTrackList = false
			}
		}
	}
	return result, numberByName
}

func scanWavs(stationName string) map[string][]WavSample {
	trackDir := filepath.Join(WAV_DIR, stationName, "Track")
	entries, err := os.ReadDir(trackDir)
	if err != nil {
		fmt.Printf("SKIP: %s/Track not found or unreadable\n", stationName)
		return nil
	}

	result := make(map[string][]WavSample)

	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}
		bankName := entry.Name()
		bankDir := filepath.Join(trackDir, bankName)

		files, err := os.ReadDir(bankDir)
		if err != nil {
			continue
		}
		for _, file := range files {
			name := file.Name()
			if !strings.HasPrefix(name, "sound_") || !strings.HasSuffix(name, ".wav") {
				continue
			}
			numStr := name[6 : len(name)-4]
			idx, err := strconv.Atoi(numStr)
			if err != nil {
				continue
			}
			info, err := file.Info()
			if err != nil {
				continue
			}
			size := info.Size()
			samplePts := (size - WAV_HEADER_SIZE) / BYTES_PER_SAMPLE

			result[bankName] = append(result[bankName],
				WavSample{
					Bank:      bankName,
					Index:     idx,
					SamplePts: int(samplePts),
				},
			)
		}
	}
	if len(result) == 0 {
		return nil
	}
	return result
}

func matchStation(stationName string, xmlSamples []TrackSample, banks map[string][]WavSample) {
	if len(banks) == 0 {
		return
	}

	// 按 samplePts 建立反向索引
	wavByPts := make(map[int][]WavSample)
	for _, samples := range banks {
		for _, w := range samples {
			wavByPts[w.SamplePts] = append(wavByPts[w.SamplePts], w)
		}
	}

	var results []matchResult
	prefix := ""
	for _, xml := range xmlSamples {
		samplePts := xml.SampleLength
		matches := wavByPts[samplePts]

		p, suffix := splitSoundName(xml.SoundName)
		if p != "" {
			prefix = p
		}

		var r matchResult
		r.suffix = suffix
		r.prefix = prefix
		r.station = stationName

		switch len(matches) {
		case 0:
			r.warning = fmt.Sprintf("WARNING: no file match for %s (SampleLength=%d)", xml.SoundName, xml.SampleLength)
		case 1:
			r.bank = matches[0].Bank
			r.index = matches[0].Index
		default:
			r.bank = matches[0].Bank
			r.index = matches[0].Index
			r.warning = fmt.Sprintf("WARNING: %d matches, using first", len(matches))
			for _, m := range matches {
				r.candidates = append(r.candidates, matchResult{
					bank:    m.Bank,
					index:   m.Index,
					station: stationName,
				})
			}
		}
		results = append(results, r)
	}

	// 按 suffix 排序
	slices.SortFunc(results, func(a, b matchResult) int {
		return strings.Compare(a.suffix, b.suffix)
	})

	fmt.Printf("\n// %s\n", stationName)
	for _, r := range results {
		if r.warning != "" && r.bank == "" {
			fmt.Printf("// %s\n", r.warning)
			continue
		}
		if r.warning != "" {
			fmt.Printf("\"%s\" to (\"%s\" to %d), // %s\n", r.suffix, r.bank, r.index, r.warning)
			for _, c := range r.candidates {
				fmt.Printf("//   candidate: %s/%s/sound_%d\n", c.station, c.bank, c.index)
			}
		} else {
			fmt.Printf("\"%s\" to (\"%s\" to %d),\n", r.suffix, r.bank, r.index)
		}
	}
	if prefix != "" {
		fmt.Printf("// prefix detected: %s\n", prefix)
	}
}

func main() {
	// 解析 XML
	allStations, numberByName := parseXML(XML_PATH)
	fmt.Printf("# Parsed %d stations from XML\n\n", len(allStations))

	// 按电台 Number 排序遍历
	stationKeys := make([]string, 0, len(allStations))
	for k := range allStations {
		stationKeys = append(stationKeys, k)
	}
	slices.SortFunc(stationKeys, func(a, b string) int {
		return numberByName[a] - numberByName[b]
	})

	// 只处理多 bank 的电台 (Track 下有子目录的)
	matchedCount := 0
	for _, stationName := range stationKeys {
		xmlSamples := allStations[stationName]
		banks := scanWavs(stationName)
		if banks == nil {
			continue
		}
		matchedCount++
		matchStation(stationName, xmlSamples, banks)
	}

	fmt.Printf("\n# %d stations with multi-bank Track mappings\n", matchedCount)
}
