package io.github.miuzarte.fhradio.model

object BankMapping {

    private val prefixRegex = Regex("^(HZ6_R\\d+)_(.+)$")

    // 由 tool/SampleMapper 生成, 仅多 bank 电台需要此映射
    // "HZ6_R1" -> { "BAYNK_Grin" -> ("CU1", 0), ... }
    @Suppress("SpellCheckingInspection")
    val Samples: Map<String, Map<String, Pair<String, Int>>> = mapOf(
        "HZ6_R1" to mapOf(
            "BAYNK_Grin" to ("CU1" to 0),
            "BLONDISH_SelfLove" to ("CU1" to 1),
            "BaluBrigada_SoCold" to ("CU1" to 2),
            "BarryCantSwim_CarsPassBy" to ("CU1" to 3),
            "BigWild_TooLoud" to ("CU1" to 4),
            "CAPYAC_UKnowY" to ("CU1" to 5),
            "CRi_HoldYou" to ("CU1" to 6),
            "CutCopy_Feat_KateBollinger_BelongToYou" to ("CU1" to 7),
            "DombreskyChaney_Running_feat_KLP" to ("CU1" to 8),
            "ElaMinus_Broken" to ("CU1" to 9),
            "EmpireOfTheSun_CherryBlossom" to ("CU1" to 10),
            "EmpireOfTheSun_CherryBlossom_FI" to ("CU1" to 11),
            "GilliganMoss_DoItForYourself" to ("CU1" to 12),
            "HauteFreddy_ShyGirl" to ("CU1" to 13),
            "HikaruUtada_Electricity_SaluteRemix" to ("CU1" to 14),
            "Joji_777" to ("CU1" to 15),
            "LPGiobbi_YouAre" to ("CU1" to 16),
            "Lane8_YouWithKasablanca" to ("CU1" to 17),
            "Mascolo_WhereYouBeen" to ("CU1" to 18),
            "MilkTalk_EnchantedStranger" to ("Disk" to 0),
            "MilkTalk_EnchantedStranger_ID" to ("Disk" to 1),
            "Mo_KeepMoving" to ("CU1" to 19),
            "PassionPitSofiTukker_Sleepyhead2025" to ("CU1" to 20),
            "PoolsideSatinJackets_PullTogether" to ("CU1" to 21),
            "PorterRobinson_Cheerleader" to ("CU1" to 22),
            "Royksopp_WhatElseIsThere_DJTennisRemix" to ("CU1" to 23),
            "SHIMA_EIYAA" to ("CU1" to 24),
            "SHIMA_EIYAA_LI" to ("CU1" to 25),
            "SHIMA_Rebirth" to ("Disk" to 2),
            "SHIMA_Rebirth_ID" to ("Disk" to 3),
            "SatinJacketsSeintMonet_Control" to ("CU1" to 26),
            "TOKiMONSTA_EnjoyYourLife" to ("CU1" to 27),
            "TameImpala_Dracula" to ("CU1" to 28),
            "TheKnocksDragonette_Revelation" to ("CU1" to 29),
            "Tycho_Totem" to ("CU1" to 30),
            "nimino_Better" to ("CU1" to 31),
        ),
        "HZ6_R2" to mapOf(
            "ALIGNEllieD_Walls" to ("CU1" to 0),
            "AnnaLunoe_DeepBlueSea" to ("CU1" to 1),
            "CalvinHarris_BlessingsOddMobRemix" to ("CU1" to 2),
            "CamdenCoxPunctualShiftK3Y_SurroundMe" to ("CU1" to 3),
            "ConfidenceMan_ICANTLOSEYOU" to ("CU1" to 4),
            "DLGxYungBae_OnTheDash" to ("CU1" to 5),
            "DanielAllen_CanItBeEasy" to ("CU1" to 6),
            "DomDolla_Feat_Daya_DreaminEliBrownRemix" to ("CU1" to 7),
            "FISHER_Stay" to ("CU1" to 8),
            "Feiertag_Embers" to ("CU1" to 9),
            "GREY_IDK" to ("CU1" to 10),
            "Gryffin_Feat_JuliaChurch_SpinMeSlowly" to ("CU1" to 11),
            "Haywyre_Chromatically" to ("CU1" to 12),
            "Haywyre_Chromatically_DJMontage" to ("CU1" to 13),
            "ISOxo_how2fly" to ("CU1" to 14),
            "ItsMurphEmiGrace_StoneColdEyes" to ("CU1" to 15),
            "Lindstrom_Cirkl" to ("CU1" to 16),
            "MarshallJeffersonBartSkils_SweetHarmony" to ("CU1" to 17),
            "MilkTalk_SayonaraAlpinistMacrossRemix" to ("CU1" to 18),
            "Ninajirachi_Infohazard" to ("CU1" to 19),
            "PrettyGirl_Rewind" to ("CU1" to 20),
            "PunctualHannahBoleyn_Eden" to ("CU1" to 21),
            "Rusko_RubixCube" to ("CU1" to 22),
            "SnakehipsDijahSB_PipeDown" to ("CU1" to 23),
            "Subtronics_Feat_Linney_Friends" to ("CU1" to 24),
            "TEED_TheEcho" to ("CU1" to 25),
            "Tourist_Outside" to ("CU1" to 26),
            "Urbandawn_YukiTouge" to ("Disk" to 0),
            "Urbandawn_YukiTouge_ID" to ("Disk" to 1),
        ),
    )

    fun lookup(soundName: String): Pair<String, Int>? {
        val match = prefixRegex.find(soundName) ?: return null
        val prefix = match.groupValues[1]
        val suffix = match.groupValues[2]
        return Samples[prefix]?.get(suffix)
    }

    fun isMultiBank(soundName: String): Boolean {
        val match = prefixRegex.find(soundName) ?: return false
        return match.groupValues[1] in Samples
    }
}
