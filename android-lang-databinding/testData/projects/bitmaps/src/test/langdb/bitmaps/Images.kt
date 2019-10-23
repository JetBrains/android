package test.langdb.bitmaps

/**
 * %d - Index between 3 and 6 (for site load balancing)
 * %s - unique ID prefix for an image
 * %s - "s240-c" for thumbs, "s1024" for high res
 * %s - file name
 */
private const val URL_TEMPLATE = "https://lh%d.googleusercontent.com/%s/%s/%s.jpg"

private const val THUMB_GROUP = "s240-c"
private const val IMAGE_GROUP = "s1024"

data class ImageEntry(private val id: String, private val filename: String) {
    private val index = (3..6).random()

    fun toThumbUrl() = URL_TEMPLATE.format(index, id, THUMB_GROUP, filename)
    fun toImageUrl() = URL_TEMPLATE.format(index, id, IMAGE_GROUP, filename)
}

/**
 * Images from the Diplaying Bitmaps github sample.
 *
 * See: https://github.com/googlesamples/android-DisplayingBitmaps
 */
val imageEntries = arrayOf(
    ImageEntry("-55osAWw3x0Q/URquUtcFr5I/AAAAAAAAAbs/rWlj1RUKrYI", "A%252520Photographer"),
    ImageEntry("--dq8niRp7W4/URquVgmXvgI/AAAAAAAAAbs/-gnuLQfNnBA", "A%252520Song%252520of%252520Ice%252520and%252520Fire"),
    ImageEntry("-7qZeDtRKFKc/URquWZT1gOI/AAAAAAAAAbs/hqWgteyNXsg", "Another%252520Rockaway%252520Sunset"),
    ImageEntry("--L0Km39l5J8/URquXHGcdNI/AAAAAAAAAbs/3ZrSJNrSomQ", "Antelope%252520Butte"),
    ImageEntry("-8HO-4vIFnlw/URquZnsFgtI/AAAAAAAAAbs/WT8jViTF7vw", "Antelope%252520Hallway"),
    ImageEntry("-WIuWgVcU3Qw/URqubRVcj4I/AAAAAAAAAbs/YvbwgGjwdIQ", "Antelope%252520Walls"),
    ImageEntry("-UBmLbPELvoQ/URqucCdv0kI/AAAAAAAAAbs/IdNhr2VQoQs", "Apre%2525CC%252580s%252520la%252520Pluie"),
    ImageEntry("-s-AFpvgSeew/URquc6dF-JI/AAAAAAAAAbs/Mt3xNGRUd68", "Backlit%252520Cloud"),
    ImageEntry("-bvmif9a9YOQ/URquea3heHI/AAAAAAAAAbs/rcr6wyeQtAo", "Bee%252520and%252520Flower"),
    ImageEntry("-n7mdm7I7FGs/URqueT_BT-I/AAAAAAAAAbs/9MYmXlmpSAo", "Bonzai%252520Rock%252520Sunset"),
    ImageEntry("-4CN4X4t0M1k/URqufPozWzI/AAAAAAAAAbs/8wK41lg1KPs", "Caterpillar"),
    ImageEntry("-rrFnVC8xQEg/URqufdrLBaI/AAAAAAAAAbs/s69WYy_fl1E", "Chess"),
    ImageEntry("-WVpRptWH8Yw/URqugh-QmDI/AAAAAAAAAbs/E-MgBgtlUWU", "Chihuly"),
    ImageEntry("-0BDXkYmckbo/URquhKFW84I/AAAAAAAAAbs/ogQtHCTk2JQ", "Closed%252520Door"),
    ImageEntry("-PyggXXZRykM/URquh-kVvoI/AAAAAAAAAbs/hFtDwhtrHHQ", "Colorado%252520River%252520Sunset"),
    ImageEntry("-ZAs4dNZtALc/URquikvOCWI/AAAAAAAAAbs/DXz4h3dll1Y", "Colors%252520of%252520Autumn"),
    ImageEntry("-GztnWEIiMz8/URqukVCU7bI/AAAAAAAAAbs/jo2Hjv6MZ6M", "Countryside"),
    ImageEntry("-bEg9EZ9QoiM/URquklz3FGI/AAAAAAAAAbs/UUuv8Ac2BaE", "Death%252520Valley%252520-%252520Dunes"),
    ImageEntry("-ijQJ8W68tEE/URqulGkvFEI/AAAAAAAAAbs/zPXvIwi_rFw", "Delicate%252520Arch"),
    ImageEntry("-Oh8mMy2ieng/URqullDwehI/AAAAAAAAAbs/TbdeEfsaIZY", "Despair"),
    ImageEntry("-gl0y4UiAOlk/URqumC_KjBI/AAAAAAAAAbs/PM1eT7dn4oo", "Eagle%252520Fall%252520Sunrise"),
    ImageEntry("-hYYHd2_vXPQ/URqumtJa9eI/AAAAAAAAAbs/wAalXVkbSh0", "Electric%252520Storm"),
    ImageEntry("-PyY_yiyjPTo/URqunUOhHFI/AAAAAAAAAbs/azZoULNuJXc", "False%252520Kiva"),
    ImageEntry("-PYvLVdvXywk/URqunwd8hfI/AAAAAAAAAbs/qiMwgkFvf6I", "Fitzgerald%252520Streaks"),
    ImageEntry("-KIR_UobIIqY/URquoCZ9SlI/AAAAAAAAAbs/Y4d4q8sXu4c", "Foggy%252520Sunset"),
    ImageEntry("-9lzOk_OWZH0/URquoo4xYoI/AAAAAAAAAbs/AwgzHtNVCwU", "Frantic"),
    ImageEntry("-0X3JNaKaz48/URqupH78wpI/AAAAAAAAAbs/lHXxu_zbH8s", "Golden%252520Gate%252520Afternoon"),
    ImageEntry("-95sb5ag7ABc/URqupl95RDI/AAAAAAAAAbs/g73R20iVTRA", "Golden%252520Gate%252520Fog"),
    ImageEntry("-JB9v6rtgHhk/URqup21F-zI/AAAAAAAAAbs/64Fb8qMZWXk", "Golden%252520Grass"),
    ImageEntry("-EIBGfnuLtII/URquqVHwaRI/AAAAAAAAAbs/FA4McV2u8VE", "Grand%252520Teton"),
    ImageEntry("-WoMxZvmN9nY/URquq1v2AoI/AAAAAAAAAbs/grj5uMhL6NA", "Grass%252520Closeup"),
    ImageEntry("-6hZiEHXx64Q/URqurxvNdqI/AAAAAAAAAbs/kWMXM3o5OVI", "Green%252520Grass"),
    ImageEntry("-6LVb9OXtQ60/URquteBFuKI/AAAAAAAAAbs/4F4kRgecwFs", "Hanging%252520Leaf"),
    ImageEntry("-zAvf__52ONk/URqutT_IuxI/AAAAAAAAAbs/D_bcuc0thoU", "Highway%2525201"),
    ImageEntry("-H4SrUg615rA/URquuL27fXI/AAAAAAAAAbs/4aEqJfiMsOU", "Horseshoe%252520Bend%252520Sunset"),
    ImageEntry("-JhFi4fb_Pqw/URquuX-QXbI/AAAAAAAAAbs/IXpYUxuweYM", "Horseshoe%252520Bend"),
    ImageEntry("-UGgssvFRJ7g/URquueyJzGI/AAAAAAAAAbs/yYIBlLT0toM", "Into%252520the%252520Blue"),
    ImageEntry("-CH7KoupI7uI/URquu0FF__I/AAAAAAAAAbs/R7GDmI7v_G0", "Jelly%252520Fish%2525202"),
    ImageEntry("-pwuuw6yhg8U/URquvPxR3FI/AAAAAAAAAbs/VNGk6f-tsGE", "Jelly%252520Fish%2525203"),
    ImageEntry("-GoUQVw1fnFw/URquv6xbC0I/AAAAAAAAAbs/zEUVTQQ43Zc", "Kauai"),
    ImageEntry("-8QdYYQEpYjw/URquwvdh88I/AAAAAAAAAbs/cktDy-ysfHo", "Kyoto%252520Sunset"),
    ImageEntry("-vPeekyDjOE0/URquwzJ28qI/AAAAAAAAAbs/qxcyXULsZrg", "Lake%252520Tahoe%252520Colors"),
    ImageEntry("-xBPxWpD4yxU/URquxWHk8AI/AAAAAAAAAbs/ARDPeDYPiMY", "Lava%252520from%252520the%252520Sky"),
    ImageEntry("-897VXrJB6RE/URquxxxd-5I/AAAAAAAAAbs/j-Cz4T4YvIw", "Leica%25252050mm%252520Summilux"),
    ImageEntry("-qSJ4D4iXzGo/URquyDWiJ1I/AAAAAAAAAbs/k2pBXeWehOA", "Leica%25252050mm%252520Summilux"),
    ImageEntry("-dwlPg83vzLg/URquylTVuFI/AAAAAAAAAbs/G6SyQ8b4YsI", "Leica%252520M8%252520%252528Front%252529"),
    ImageEntry("-R3_EYAyJvfk/URquzQBv8eI/AAAAAAAAAbs/b9xhpUM3pEI", "Light%252520to%252520Sand"),
    ImageEntry("-fHY5h67QPi0/URqu0Cp4J1I/AAAAAAAAAbs/0lG6m94Z6vM", "Little%252520Bit%252520of%252520Paradise"),
    ImageEntry("-TzF_LwrCnRM/URqu0RddPOI/AAAAAAAAAbs/gaj2dLiuX0s", "Lone%252520Pine%252520Sunset"),
    ImageEntry("-4HdpJ4_DXU4/URqu046dJ9I/AAAAAAAAAbs/eBOodtk2_uk", "Lonely%252520Rock"),
    ImageEntry("-erbF--z-W4s/URqu1ajSLkI/AAAAAAAAAbs/xjDCDO1INzM", "Longue%252520Vue"),
    ImageEntry("-0CXJRdJaqvc/URqu1opNZNI/AAAAAAAAAbs/PFB2oPUU7Lk", "Look%252520Me%252520in%252520the%252520Eye"),
    ImageEntry("-D_5lNxnDN6g/URqu2Tk7HVI/AAAAAAAAAbs/p0ddca9W__Y", "Lost%252520in%252520a%252520Field"),
    ImageEntry("-flsqwMrIk2Q/URqu24PcmjI/AAAAAAAAAbs/5ocIH85XofM", "Marshall%252520Beach%252520Sunset"),
    ImageEntry("-Y4lgryEVTmU/URqu28kG3gI/AAAAAAAAAbs/OjXpekqtbJ4", "Mono%252520Lake%252520Blue"),
    ImageEntry("-AaHAJPmcGYA/URqu3PIldHI/AAAAAAAAAbs/lcTqk1SIcRs", "Monument%252520Valley%252520Overlook"),
    ImageEntry("-vKxfdQ83dQA/URqu31Yq_BI/AAAAAAAAAbs/OUoGk_2AyfM", "Moving%252520Rock"),
    ImageEntry("-CG62QiPpWXg/URqu4ia4vRI/AAAAAAAAAbs/0YOdqLAlcAc", "Napali%252520Coast"),
    ImageEntry("-wdGrP5PMmJQ/URqu5PZvn7I/AAAAAAAAAbs/m0abEcdPXe4", "One%252520Wheel"),
    ImageEntry("-6WS5DoCGuOA/URqu5qx1UgI/AAAAAAAAAbs/giMw2ixPvrY", "Open%252520Sky"),
    ImageEntry("-u8EHKj8G8GQ/URqu55sM6yI/AAAAAAAAAbs/lIXX_GlTdmI", "Orange%252520Sunset"),
    ImageEntry("-74Z5qj4bTDE/URqu6LSrJrI/AAAAAAAAAbs/XzmVkw90szQ", "Orchid"),
    ImageEntry("-lEQE4h6TePE/URqu6t_lSkI/AAAAAAAAAbs/zvGYKOea_qY", "Over%252520there"),
    ImageEntry("-cauH-53JH2M/URqu66v_USI/AAAAAAAAAbs/EucwwqclfKQ", "Plumes"),
    ImageEntry("-eDLT2jHDoy4/URqu7axzkAI/AAAAAAAAAbs/iVZE-xJ7lZs", "Rainbokeh"),
    ImageEntry("-j1NLqEFIyco/URqu8L1CGcI/AAAAAAAAAbs/aqZkgX66zlI", "Rainbow"),
    ImageEntry("-DRnqmK0t4VU/URqu8XYN9yI/AAAAAAAAAbs/LgvF_592WLU", "Rice%252520Fields"),
    ImageEntry("-hwh1v3EOGcQ/URqu8qOaKwI/AAAAAAAAAbs/IljRJRnbJGw", "Rockaway%252520Fire%252520Sky"),
    ImageEntry("-wjV6FQk7tlk/URqu9jCQ8sI/AAAAAAAAAbs/RyYUpdo-c9o", "Rockaway%252520Flow"),
    ImageEntry("-6cAXNfo7D20/URqu-BdzgPI/AAAAAAAAAbs/OmsYllzJqwo", "Rockaway%252520Sunset%252520Sky"),
    ImageEntry("-sl8fpGPS-RE/URqu_BOkfgI/AAAAAAAAAbs/Dg2Fv-JxOeg", "Russian%252520Ridge%252520Sunset"),
    ImageEntry("-gVtY36mMBIg/URqu_q91lkI/AAAAAAAAAbs/3CiFMBcy5MA", "Rust%252520Knot"),
    ImageEntry("-GHeImuHqJBE/URqu_FKfVLI/AAAAAAAAAbs/axuEJeqam7Q", "Sailing%252520Stones"),
    ImageEntry("-hBbYZjTOwGc/URqu_ycpIrI/AAAAAAAAAbs/nAdJUXnGJYE", "Seahorse"),
    ImageEntry("-Iwi6-i6IexY/URqvAYZHsVI/AAAAAAAAAbs/5ETWl4qXsFE", "Shinjuku%252520Street"),
    ImageEntry("-amhnySTM_MY/URqvAlb5KoI/AAAAAAAAAbs/pFCFgzlKsn0", "Sierra%252520Heavens"),
    ImageEntry("-dJgjepFrYSo/URqvBVJZrAI/AAAAAAAAAbs/v-F5QWpYO6s", "Sierra%252520Sunset"),
    ImageEntry("-Z4zGiC5nWdc/URqvBdEwivI/AAAAAAAAAbs/ZRZR1VJ84QA", "Sin%252520Lights"),
    ImageEntry("-_0cYiWW8ccY/URqvBz3iM4I/AAAAAAAAAbs/9N_Wq8MhLTY", "Starry%252520Lake"),
    ImageEntry("-A9LMoRyuQUA/URqvCYx_JoI/AAAAAAAAAbs/s7sde1Bz9cI", "Starry%252520Night"),
    ImageEntry("-KtLJ3k858eY/URqvC_2h_bI/AAAAAAAAAbs/zzEBImwDA_g", "Stream"),
    ImageEntry("-dFB7Lad6RcA/URqvDUftwWI/AAAAAAAAAbs/BrhoUtXTN7o", "Strip%252520Sunset"),
    ImageEntry("-at6apgFiN20/URqvDyffUZI/AAAAAAAAAbs/clABCx171bE", "Sunset%252520Hills"),
    ImageEntry("-7-EHhtQthII/URqvEYTk4vI/AAAAAAAAAbs/QSJZoB3YjVg", "Tenaya%252520Lake%2525202"),
    ImageEntry("-8MrjV_a-Pok/URqvFC5repI/AAAAAAAAAbs/9inKTg9fbCE", "Tenaya%252520Lake"),
    ImageEntry("-B1HW-z4zwao/URqvFWYRwUI/AAAAAAAAAbs/8Peli53Bs8I", "The%252520Cave%252520BW"),
    ImageEntry("-PO4E-xZKAnQ/URqvGRqjYkI/AAAAAAAAAbs/42nyADFsXag", "The%252520Fisherman"),
    ImageEntry("-iLyZlzfdy7s/URqvG0YScdI/AAAAAAAAAbs/1J9eDKmkXtk", "The%252520Night%252520is%252520Coming"),
    ImageEntry("-G-k7YkkUco0/URqvHhah6fI/AAAAAAAAAbs/_taQQG7t0vo", "The%252520Road"),
    ImageEntry("-h-ALJt7kSus/URqvIThqYfI/AAAAAAAAAbs/ejiv35olWS8", "Tokyo%252520Heights"),
    ImageEntry("-Hy9k-TbS7xg/URqvIjQMOxI/AAAAAAAAAbs/RSpmmOATSkg", "Tokyo%252520Highway"),
    ImageEntry("-83oOvMb4OZs/URqvJL0T7lI/AAAAAAAAAbs/c5TECZ6RONM", "Tokyo%252520Smog"),
    ImageEntry("-FB-jfgREEfI/URqvJI3EXAI/AAAAAAAAAbs/XfyweiRF4v8", "Tufa%252520at%252520Night"),
    ImageEntry("-vngKD5Z1U8w/URqvJUCEgPI/AAAAAAAAAbs/ulxCMVcU6EU", "Valley%252520Sunset"),
    ImageEntry("-DOz5I2E2oMQ/URqvKMND1kI/AAAAAAAAAbs/Iqf0IsInleo", "Windmill%252520Sunrise"),
    ImageEntry("-biyiyWcJ9MU/URqvKculiAI/AAAAAAAAAbs/jyPsCplJOpE", "Windmill"),
    ImageEntry("-PDT167_xRdA/URqvK36mLcI/AAAAAAAAAbs/oi2ik9QseMI", "Windmills"),
    ImageEntry("-kI_QdYx7VlU/URqvLXCB6gI/AAAAAAAAAbs/N31vlZ6u89o", "Yet%252520Another%252520Rockaway%252520Sunset"),
    ImageEntry("-e9NHZ5k5MSs/URqvMIBZjtI/AAAAAAAAAbs/1fV810rDNfQ", "Yosemite%252520Tree")
)