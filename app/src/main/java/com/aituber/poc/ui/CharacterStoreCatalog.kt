package com.aituber.poc.ui

internal data class CharacterStoreEntry(
    val id: String,
    val displayName: String,
    val source: String,
    val sourceUrl: String,
    val downloadUrl: String,
    val previewUrl: String,
    val directDownload: Boolean = false
)

internal object CharacterStoreCatalog {
    val entries: List<CharacterStoreEntry> = listOf(
        CharacterStoreEntry(
            id = "arch_chan",
            displayName = "Arch Chan",
            source = "GitHub",
            sourceUrl = "https://github.com/Speykious/arch-chan",
            downloadUrl = "https://github.com/Speykious/arch-chan/archive/refs/heads/main.zip",
            previewUrl = "https://opengraph.githubassets.com/6370eee9228799331896bbe10b38ef920e6b8f1cd6a462133f6a915a2045a0ce/Speykious/arch-chan",
            directDownload = true
        ),
        CharacterStoreEntry(
            id = "goth_lolita",
            displayName = "ダウナーなゴスロリ少女",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/zh-tw/items/8589066",
            downloadUrl = "https://booth.pm/zh-tw/items/8589066",
            previewUrl = "https://booth.pximg.net/c/620x620/3f120f24-c61d-47f7-aa59-da982dd8a18f/i/8589066/e2d0ee89-e7ba-4a47-a16e-9e5e1daba4c8_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "office_girl",
            displayName = "オフィスの女の子",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/ja/items/4304615",
            downloadUrl = "https://booth.pm/ja/items/4304615",
            previewUrl = "https://booth.pximg.net/c/620x620/c69a8eec-60be-4b06-a043-56e78c17e092/i/4304615/f3ef002e-7c93-496c-892e-4cfa35b56276_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "generic_boy01",
            displayName = "generic_boy01",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/ja/items/5235226",
            downloadUrl = "https://booth.pm/ja/items/5235226",
            previewUrl = "https://booth.pximg.net/c/620x620/393c2f14-b02d-49d3-a4bc-b819b7489fbf/i/5235226/8dc8b009-f166-45ab-aae4-036ccd112cef_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "scarlett",
            displayName = "Scarlett",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/ja/items/6499001",
            downloadUrl = "https://booth.pm/ja/items/6499001",
            previewUrl = "https://booth.pximg.net/c/620x620/da520e9a-71a8-4df7-8d9c-b029e7dfd3cf/i/6499001/dd042ed5-e402-4b35-9a42-af7800bafb7e_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "alexia",
            displayName = "Alexia",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/ja/items/5576188",
            downloadUrl = "https://booth.pm/ja/items/5576188",
            previewUrl = "https://booth.pximg.net/c/620x620/5ea662ff-f1b5-4846-ae83-9cc682a9ef27/i/5576188/df51f6ab-865d-48c6-b70f-235c2debbfe3_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "gloria",
            displayName = "Gloria",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/zh-tw/items/4860071",
            downloadUrl = "https://booth.pm/zh-tw/items/4860071",
            previewUrl = "https://booth.pximg.net/c/620x620/c4330768-84bd-44f0-9760-6285854d81c5/i/4860071/0ee6863f-96b2-4b6f-97c5-37d71157ebbd_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "snow_bear_girl",
            displayName = "雪熊少女",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/zh-tw/items/7763817",
            downloadUrl = "https://booth.pm/zh-tw/items/7763817",
            previewUrl = "https://booth.pximg.net/c/620x620/12bc9ed4-4f56-4b8f-9741-772cab0a5540/i/7763817/5ed8d5c7-5f3e-4a68-b631-312b496ab674_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "silver_hair_4_costumes",
            displayName = "Silver-haired girl",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/ja/items/4154562",
            downloadUrl = "https://booth.pm/ja/items/4154562",
            previewUrl = "https://booth.pximg.net/c/620x620/e6129b72-54ab-4167-865c-1bd66ea02e66/i/4154562/4b6411c7-9b5f-4f55-a4d0-248afcb0d0fb_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "mayoi",
            displayName = "Mayoi",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/zh-tw/items/8447197",
            downloadUrl = "https://booth.pm/zh-tw/items/8447197",
            previewUrl = "https://booth.pximg.net/c/620x620/5ecf35cf-4d34-4799-87d9-9ae9d9a7f780/i/8447197/720d17d9-f700-45ed-8680-132a77deaa7a_base_resized.jpg"
        )
    )
}
