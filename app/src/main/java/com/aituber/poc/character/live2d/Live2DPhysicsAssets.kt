package com.aituber.poc.character.live2d

object Live2DPhysicsAssets {
    const val HARU_PHYSICS_FILE = "Haru.physics3.json"
    val HARU_INPUT_PARAMETER_IDS = listOf(
        "ParamAngleX",
        "ParamAngleZ",
        "ParamBodyAngleX",
        "ParamBodyAngleZ"
    )
    val HARU_OUTPUT_PARAMETER_IDS = listOf(
        "ParamHairFront",
        "ParamHairSide",
        "ParamHairBack",
        "ParamScarf"
    )
    const val HARU_TOTAL_INPUT_COUNT = 14
    const val HARU_TOTAL_OUTPUT_COUNT = 4
}
