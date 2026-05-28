package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.*

object RadioXmlParser {

    fun parse(xml: String): RadioInfo {
        val root = parseElement(xml.trimStart(), 0).node
        val version = root.attr("Version").toInt()
        val djInterval = root.attr("TimeBetweenMidTrackDJLines").toInt()
        val recentSize = root.attr("RecentlyPlayedMaxSize").toInt()

        val stations = root.children
            .first { it.name == "RadioStations" }
            .children
            .map { buildStation(it) }

        // 虚拟电台 (如 Streamer Mode) 回填 parentStation
        val nameToStation = mutableMapOf<String, RadioStation>()
        stations.forEach { s ->
            s.track.forEach { nameToStation.getOrPut(it.soundName) { s } }
            s.stinger.forEach { nameToStation.getOrPut(it.soundName) { s } }
            s.dj.forEach { nameToStation.getOrPut(it.soundName) { s } }
        }
        stations.forEach { s ->
            s.track.forEach { sample ->
                val parent = nameToStation[sample.soundName]
                if (parent != null && parent !== s) sample.parentStation = parent
            }
            s.stinger.forEach { sample ->
                val parent = nameToStation[sample.soundName]
                if (parent != null && parent !== s) sample.parentStation = parent
            }
            s.dj.forEach { sample ->
                val parent = nameToStation[sample.soundName]
                if (parent != null && parent !== s) sample.parentStation = parent
            }
        }

        return RadioInfo(version, djInterval, recentSize, stations)
    }

    private fun buildStation(el: XmlNode): RadioStation {
        val name = el.attr("Name")
        val number = el.attr("Number").toInt()
        val djCharId = el.attr("DJCharID").toInt()

        val banks = el.children.filter { it.name == "Banks" }.map { Bank(it.attr("Name")) }

        val sampleLists = el.children.filter { it.name == "SampleList" }
        val track = sampleLists
            .find { it.attr("Type") == SampleType.Track.toString() }
            ?.children?.map { buildTrack(it) }
        val dj = sampleLists
            .find { it.attr("Type") == SampleType.DJ.toString() }
            ?.children?.map { buildDj(it) }
        val stinger = sampleLists
            .find { it.attr("Type") == SampleType.Stinger.toString() }
            ?.children?.map { buildStinger(it) }

        val playLists = el.children.filter { it.name == "PlayList" }.map { buildPlayList(it) }
        val freeRoam = playLists.find { it.type == PlayListType.FreeRoam }
        val event = playLists.find { it.type == PlayListType.Event }
        val shortStinger = playLists.find { it.type == PlayListType.ShortStinger }

        return RadioStation(
            name = name,
            number = number,
            djCharId = djCharId,
            banks = banks,
            track = track ?: emptyList(),
            dj = dj ?: emptyList(),
            stinger = stinger ?: emptyList(),
            freeRoam = freeRoam ?: PlayList(type = PlayListType.FreeRoam),
            event = event ?: PlayList(type = PlayListType.Event),
            shortStinger = shortStinger ?: PlayList(type = PlayListType.ShortStinger),
        )
    }

    private fun buildTrack(el: XmlNode) =
        TrackSample(
            soundName = el.attr("SoundName"),
            sampleLength = el.attr("SampleLength").toInt(),
            sampleRate = el.attr("SampleRate").toInt(),
            displayName = el.attr("DisplayName"),
            artist = el.attr("Artist"),
            isXCloudModeSafe = el.attr("IsXCloudModeSafe").toBoolean(),

            marker = el.children.filter { it.name == "Marker" }.mapNotNull { buildMarkerOrNull(it) },
            loop = el.children.filter { it.name == "Loop" }.mapNotNull { buildLoopOrNull(it) },
            bpmList = el.children.filter { it.name == "BPM" }.map { buildBpm(it) },
        )

    private fun buildStinger(el: XmlNode) =
        StingerSample(
            soundName = el.attr("SoundName"),
            sampleLength = el.attr("SampleLength").toInt(),
            sampleRate = el.attr("SampleRate").toInt(),

            marker = el.children.filter { it.name == "Marker" }.mapNotNull { buildMarkerOrNull(it) },
        )

    private fun buildDj(el: XmlNode) =
        DjSample(
            soundName = el.attr("SoundName"),
            sampleLength = el.attr("SampleLength").toInt(),
            sampleRate = el.attr("SampleRate").toInt(),
            gameEvent = el.attr("GameEvent"),
        )

    private fun buildMarkerOrNull(el: XmlNode): Marker? =
        try {
            Marker(
                name = Marker.Type.valueOf(el.attr("Name")),
                position = el.attr("Position").toInt(),
            )
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun buildLoopOrNull(el: XmlNode): Loop? =
        try {
            Loop.valueOf(el.attr("Name"))
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun buildBpm(el: XmlNode) =
        Bpm(
            value = el.attr("Value").toFloat(),
            start = el.attr("Start").toInt(),
        )

    private fun buildPlayList(el: XmlNode): PlayList {
        val type = when (el.attr("Type")) {
            "FreeRoam" -> PlayListType.FreeRoam
            "Event" -> PlayListType.Event
            "ShortStinger" -> PlayListType.ShortStinger
            else -> PlayListType.FreeRoam
        }
        val entries = el.children
            .filter { it.name == "Entry" }
            .map { PlayListEntry(name = it.attr("Name")) }
        return PlayList(type = type, entries = entries)
    }

    // --- minimal XML parser ---

    private data class XmlNode(
        val name: String,
        val attributes: Map<String, String>,
        val children: List<XmlNode>,
    ) {
        fun attr(key: String): String = attributes[key] ?: ""
    }

    private data class ParseResult(
        val node: XmlNode,
        val nextPos: Int,
    )

    private fun unescapeXml(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")

    private fun parseElement(xml: String, start: Int): ParseResult {
        var pos = skipPast(xml, start, '<')
        pos = skipDeclarationOrComment(xml, pos)

        val nameEnd = xml.indexOfAny(charArrayOf(' ', '/', '>'), pos)
            .takeIf { it >= 0 }
            ?: xml.length
        val name = xml.substring(pos, nameEnd)
        pos = nameEnd

        val attributes = mutableMapOf<String, String>()
        var selfClosing = false

        while (pos < xml.length) {
            pos = skipWhitespace(xml, pos)
            when (xml[pos]) {
                '/' -> {
                    pos++
                    selfClosing = true
                }

                '>' -> {
                    pos++
                    break
                }

                else -> {
                    val eq = xml.indexOf('=', pos)
                    if (eq < 0) break
                    val attrName = xml.substring(pos, eq)
                    pos = eq + 1
                    val quote = xml[pos]
                    pos++
                    val valEnd = xml.indexOf(quote, pos)
                    if (valEnd < 0) break
                    val attrValue = unescapeXml(xml.substring(pos, valEnd))
                    pos = valEnd + 1
                    attributes[attrName] = attrValue
                }
            }
        }

        if (selfClosing) {
            return ParseResult(XmlNode(name, attributes, emptyList()), pos)
        }

        val closingTag = "</$name>"
        val children = mutableListOf<XmlNode>()
        while (pos < xml.length) {
            pos = skipWhitespace(xml, pos)
            if (xml.regionMatches(pos, closingTag, 0, closingTag.length)) {
                pos += closingTag.length
                break
            }
            val child = parseElement(xml, pos)
            children.add(child.node)
            pos = child.nextPos
        }

        return ParseResult(XmlNode(name, attributes, children), pos)
    }

    private fun skipPast(xml: String, start: Int, c: Char): Int {
        val i = xml.indexOf(c, start)
        return if (i >= 0) i + 1 else xml.length
    }

    private fun skipWhitespace(xml: String, start: Int): Int {
        var p = start
        while (p < xml.length && xml[p].isWhitespace()) p++
        return p
    }

    private fun skipDeclarationOrComment(xml: String, start: Int): Int {
        var pos = start
        if (xml.regionMatches(pos, "?xml", 0, 4)) {
            pos = xml.indexOf("?>", pos).takeIf { it >= 0 }?.plus(2) ?: pos
            pos = skipPast(xml, pos, '<')
        }
        while (xml.regionMatches(pos, "!--", 0, 3)) {
            pos = xml.indexOf("-->", pos).takeIf { it >= 0 }?.plus(3) ?: pos
            pos = skipWhitespace(xml, pos)
            if (pos < xml.length && xml[pos] == '<') pos++
        }
        return pos
    }
}
