package com.lightledger.app.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 分类/账本可选的内置线性图标集合。
 * 图标以字符串 key 存入数据库，渲染时映射回 ImageVector，避免存枚举导致迁移困难。
 *
 * 全量约 110 个，按生活场景分组；编辑分类/账本时以网格全量列出。
 * 选取原则：Material Icons Outlined 中长期稳定存在的图标名，避免随库版本消失。
 */
object IconLibrary {

    data class IconItem(val key: String, val icon: ImageVector)

    private val all = listOf(
        // ---------- 餐饮 ----------
        IconItem("restaurant", Icons.Outlined.Restaurant),
        IconItem("restaurant_menu", Icons.Outlined.RestaurantMenu),
        IconItem("local_cafe", Icons.Outlined.LocalCafe),
        IconItem("coffee", Icons.Outlined.Coffee),
        IconItem("local_bar", Icons.Outlined.LocalBar),
        IconItem("wine_bar", Icons.Outlined.WineBar),
        IconItem("sports_bar", Icons.Outlined.SportsBar),
        IconItem("fastfood", Icons.Outlined.Fastfood),
        IconItem("lunch_dining", Icons.Outlined.LunchDining),
        IconItem("dinner_dining", Icons.Outlined.DinnerDining),
        IconItem("local_dining", Icons.Outlined.LocalDining),
        IconItem("local_pizza", Icons.Outlined.LocalPizza),
        IconItem("icecream", Icons.Outlined.Icecream),
        IconItem("cake", Icons.Outlined.Cake),
        // ---------- 购物 ----------
        IconItem("shopping_bag", Icons.Outlined.ShoppingBag),
        IconItem("shopping_cart", Icons.Outlined.ShoppingCart),
        IconItem("storefront", Icons.Outlined.Storefront),
        IconItem("store", Icons.Outlined.Store),
        IconItem("local_mall", Icons.Outlined.LocalMall),
        IconItem("redeem", Icons.Outlined.Redeem),
        IconItem("card_giftcard", Icons.Outlined.CardGiftcard),
        IconItem("loyalty", Icons.Outlined.Loyalty),
        IconItem("sell", Icons.Outlined.Sell),
        // ---------- 交通出行 ----------
        IconItem("directions_bus", Icons.Outlined.DirectionsBus),
        IconItem("directions_car", Icons.Outlined.DirectionsCar),
        IconItem("directions_bike", Icons.Outlined.DirectionsBike),
        IconItem("two_wheeler", Icons.Outlined.TwoWheeler),
        IconItem("train", Icons.Outlined.Train),
        IconItem("tram", Icons.Outlined.Tram),
        IconItem("subway", Icons.Outlined.Subway),
        IconItem("local_taxi", Icons.Outlined.LocalTaxi),
        IconItem("airport_shuttle", Icons.Outlined.AirportShuttle),
        IconItem("directions_boat", Icons.Outlined.DirectionsBoat),
        IconItem("flight", Icons.Outlined.Flight),
        IconItem("local_gas_station", Icons.Outlined.LocalGasStation),
        IconItem("electric_car", Icons.Outlined.ElectricCar),
        IconItem("local_parking", Icons.Outlined.LocalParking),
        IconItem("confirmation_number", Icons.Outlined.ConfirmationNumber),
        // ---------- 居住家居 ----------
        IconItem("home", Icons.Outlined.Home),
        IconItem("apartment", Icons.Outlined.Apartment),
        IconItem("cottage", Icons.Outlined.Cottage),
        IconItem("hotel", Icons.Outlined.Hotel),
        IconItem("kitchen", Icons.Outlined.Kitchen),
        IconItem("chair", Icons.Outlined.Chair),
        IconItem("bed", Icons.Outlined.Bed),
        IconItem("weekend", Icons.Outlined.Weekend),
        IconItem("lightbulb", Icons.Outlined.Lightbulb),
        IconItem("plumbing", Icons.Outlined.Plumbing),
        IconItem("electrical_services", Icons.Outlined.ElectricalServices),
        IconItem("cleaning_services", Icons.Outlined.CleaningServices),
        IconItem("build", Icons.Outlined.Build),
        // ---------- 医疗健康 ----------
        IconItem("medical_services", Icons.Outlined.MedicalServices),
        IconItem("local_hospital", Icons.Outlined.LocalHospital),
        IconItem("local_pharmacy", Icons.Outlined.LocalPharmacy),
        IconItem("medication", Icons.Outlined.Medication),
        IconItem("health_and_safety", Icons.Outlined.HealthAndSafety),
        IconItem("masks", Icons.Outlined.Masks),
        // ---------- 教育办公 ----------
        IconItem("school", Icons.Outlined.School),
        IconItem("work", Icons.Outlined.Work),
        IconItem("business_center", Icons.Outlined.BusinessCenter),
        IconItem("menu_book", Icons.Outlined.MenuBook),
        IconItem("book", Icons.Outlined.Book),
        IconItem("local_library", Icons.Outlined.LocalLibrary),
        IconItem("edit_note", Icons.Outlined.EditNote),
        IconItem("calculate", Icons.Outlined.Calculate),
        IconItem("assignment", Icons.Outlined.Assignment),
        IconItem("description", Icons.Outlined.Description),
        IconItem("folder", Icons.Outlined.Folder),
        IconItem("science", Icons.Outlined.Science),
        IconItem("architecture", Icons.Outlined.Architecture),
        IconItem("translate", Icons.Outlined.Translate),
        // ---------- 数码电子 ----------
        IconItem("phone_iphone", Icons.Outlined.PhoneIphone),
        IconItem("laptop", Icons.Outlined.Laptop),
        IconItem("computer", Icons.Outlined.Computer),
        IconItem("print", Icons.Outlined.Print),
        IconItem("devices", Icons.Outlined.Devices),
        IconItem("camera_alt", Icons.Outlined.CameraAlt),
        IconItem("brush", Icons.Outlined.Brush),
        IconItem("palette", Icons.Outlined.Palette),
        // ---------- 娱乐运动 ----------
        IconItem("sports_esports", Icons.Outlined.SportsEsports),
        IconItem("videogame_asset", Icons.Outlined.VideogameAsset),
        IconItem("movie", Icons.Outlined.Movie),
        IconItem("music_note", Icons.Outlined.MusicNote),
        IconItem("headphones", Icons.Outlined.Headphones),
        IconItem("mic", Icons.Outlined.Mic),
        IconItem("theater_comedy", Icons.Outlined.TheaterComedy),
        IconItem("casino", Icons.Outlined.Casino),
        IconItem("sports_soccer", Icons.Outlined.SportsSoccer),
        IconItem("sports_basketball", Icons.Outlined.SportsBasketball),
        IconItem("fitness_center", Icons.Outlined.FitnessCenter),
        IconItem("pool", Icons.Outlined.Pool),
        IconItem("golf_course", Icons.Outlined.GolfCourse),
        // ---------- 旅行户外 ----------
        IconItem("luggage", Icons.Outlined.Luggage),
        IconItem("backpack", Icons.Outlined.Backpack),
        IconItem("map", Icons.Outlined.Map),
        IconItem("tour", Icons.Outlined.Tour),
        IconItem("beach_access", Icons.Outlined.BeachAccess),
        IconItem("terrain", Icons.Outlined.Terrain),
        IconItem("landscape", Icons.Outlined.Landscape),
        IconItem("forest", Icons.Outlined.Forest),
        IconItem("park", Icons.Outlined.Park),
        IconItem("waves", Icons.Outlined.Waves),
        IconItem("museum", Icons.Outlined.Museum),
        // ---------- 财务理财 ----------
        IconItem("payments", Icons.Outlined.Payments),
        IconItem("savings", Icons.Outlined.Savings),
        IconItem("account_balance", Icons.Outlined.AccountBalance),
        IconItem("account_balance_wallet", Icons.Outlined.AccountBalanceWallet),
        IconItem("credit_card", Icons.Outlined.CreditCard),
        IconItem("currency_exchange", Icons.Outlined.CurrencyExchange),
        IconItem("receipt", Icons.Outlined.Receipt),
        IconItem("receipt_long", Icons.Outlined.ReceiptLong),
        IconItem("attach_money", Icons.Outlined.AttachMoney),
        IconItem("paid", Icons.Outlined.Paid),
        IconItem("trending_up", Icons.Outlined.TrendingUp),
        IconItem("trending_down", Icons.Outlined.TrendingDown),
        // ---------- 生活服务 ----------
        IconItem("person", Icons.Outlined.Person),
        IconItem("group", Icons.Outlined.Group),
        IconItem("child_care", Icons.Outlined.ChildCare),
        IconItem("child_friendly", Icons.Outlined.ChildFriendly),
        IconItem("elderly", Icons.Outlined.Elderly),
        IconItem("self_improvement", Icons.Outlined.SelfImprovement),
        IconItem("spa", Icons.Outlined.Spa),
        IconItem("dry_cleaning", Icons.Outlined.DryCleaning),
        IconItem("local_laundry_service", Icons.Outlined.LocalLaundryService),
        IconItem("volunteer_activism", Icons.Outlined.VolunteerActivism),
        // ---------- 宠物自然 ----------
        IconItem("pets", Icons.Outlined.Pets),
        IconItem("eco", Icons.Outlined.Eco),
        IconItem("wb_sunny", Icons.Outlined.WbSunny),
        IconItem("cloud", Icons.Outlined.Cloud),
        // ---------- 通用符号 ----------
        IconItem("star", Icons.Outlined.Star),
        IconItem("favorite", Icons.Outlined.Favorite),
        IconItem("public", Icons.Outlined.Public),
        IconItem("place", Icons.Outlined.Place),
        IconItem("location_on", Icons.Outlined.LocationOn),
        IconItem("schedule", Icons.Outlined.Schedule),
        IconItem("event", Icons.Outlined.Event),
        IconItem("today", Icons.Outlined.Today),
        IconItem("flag", Icons.Outlined.Flag),
        IconItem("bolt", Icons.Outlined.Bolt),
        IconItem("water_drop", Icons.Outlined.WaterDrop),
        IconItem("lock", Icons.Outlined.Lock),
        IconItem("security", Icons.Outlined.Security),
        IconItem("wifi", Icons.Outlined.Wifi),
        IconItem("help", Icons.Outlined.Help),
        IconItem("settings", Icons.Outlined.Settings),
        IconItem("fingerprint", Icons.Outlined.Fingerprint),
    )

    private val map = all.associateBy { it.key }

    val keys: List<String> get() = all.map { it.key }

    fun of(key: String): ImageVector = map[key]?.icon ?: Icons.Outlined.Book

    fun nameOf(key: String): String = key

    /**
     * 图标中文名（数据字典性质，与 key 对齐维护；英文环境展示 key 的下划线空格形式）。
     */
    private val nameZh = mapOf(
        // 餐饮
        "restaurant" to "餐饮", "restaurant_menu" to "菜单", "local_cafe" to "咖啡",
        "coffee" to "咖啡", "local_bar" to "酒吧", "wine_bar" to "红酒",
        "sports_bar" to "酒馆", "fastfood" to "快餐", "lunch_dining" to "午餐",
        "dinner_dining" to "晚餐", "local_dining" to "正餐", "local_pizza" to "披萨",
        "icecream" to "冰淇淋", "cake" to "蛋糕",
        // 购物
        "shopping_bag" to "购物", "shopping_cart" to "购物车", "storefront" to "门店",
        "store" to "商店", "local_mall" to "商场", "redeem" to "兑换",
        "card_giftcard" to "礼品卡", "loyalty" to "会员", "sell" to "出售",
        // 交通
        "directions_bus" to "公交", "directions_car" to "汽车", "directions_bike" to "骑行",
        "two_wheeler" to "摩托", "train" to "火车", "tram" to "电车",
        "subway" to "地铁", "local_taxi" to "出租车", "airport_shuttle" to "机场巴士",
        "directions_boat" to "轮船", "flight" to "飞机", "local_gas_station" to "加油",
        "electric_car" to "电车", "local_parking" to "停车", "confirmation_number" to "票务",
        // 居住
        "home" to "家", "apartment" to "公寓", "cottage" to "小屋",
        "hotel" to "酒店", "kitchen" to "厨房", "chair" to "椅子",
        "bed" to "床", "weekend" to "沙发", "lightbulb" to "灯具",
        "plumbing" to "水管", "electrical_services" to "电路", "cleaning_services" to "保洁",
        "build" to "维修",
        // 医疗
        "medical_services" to "医疗", "local_hospital" to "医院", "local_pharmacy" to "药房",
        "medication" to "用药", "health_and_safety" to "健康", "masks" to "口罩",
        // 教育办公
        "school" to "学校", "work" to "工作", "business_center" to "商务",
        "menu_book" to "书本", "book" to "图书", "local_library" to "图书馆",
        "edit_note" to "笔记", "calculate" to "计算", "assignment" to "任务",
        "description" to "文档", "folder" to "文件夹", "science" to "科学",
        "architecture" to "设计", "translate" to "翻译",
        // 数码
        "phone_iphone" to "手机", "laptop" to "笔电", "computer" to "电脑",
        "print" to "打印", "devices" to "设备", "camera_alt" to "相机",
        "brush" to "画笔", "palette" to "调色",
        // 娱乐运动
        "sports_esports" to "游戏", "videogame_asset" to "电玩", "movie" to "电影",
        "music_note" to "音乐", "headphones" to "耳机", "mic" to "麦克风",
        "theater_comedy" to "演出", "casino" to "桌游", "sports_soccer" to "足球",
        "sports_basketball" to "篮球", "fitness_center" to "健身", "pool" to "游泳",
        "golf_course" to "高尔夫",
        // 旅行
        "luggage" to "行李", "backpack" to "背包", "map" to "地图",
        "tour" to "旅行", "beach_access" to "海滩", "terrain" to "山地",
        "landscape" to "风景", "forest" to "森林", "park" to "公园",
        "waves" to "海浪", "museum" to "博物馆",
        // 财务
        "payments" to "支付", "savings" to "储蓄", "account_balance" to "银行",
        "account_balance_wallet" to "钱包", "credit_card" to "信用卡",
        "currency_exchange" to "换汇", "receipt" to "收据", "receipt_long" to "账单",
        "attach_money" to "外币", "paid" to "已付", "trending_up" to "收入",
        "trending_down" to "支出",
        // 生活服务
        "person" to "个人", "group" to "团队", "child_care" to "育儿",
        "child_friendly" to "亲子", "elderly" to "长者", "self_improvement" to "修身",
        "spa" to "水疗", "dry_cleaning" to "干洗", "local_laundry_service" to "洗衣",
        "volunteer_activism" to "公益",
        // 自然宠物
        "pets" to "宠物", "eco" to "环保", "wb_sunny" to "晴天", "cloud" to "天气",
        // 通用
        "star" to "星标", "favorite" to "收藏", "public" to "全球", "place" to "地点",
        "location_on" to "定位", "schedule" to "时间", "event" to "日程",
        "today" to "今天", "flag" to "标记", "bolt" to "闪电", "water_drop" to "水滴",
        "lock" to "锁", "security" to "安全", "wifi" to "网络", "help" to "帮助",
        "settings" to "设置", "fingerprint" to "指纹",
    )

    /** 图标显示名：zh 传 true 显示中文，否则显示 key 的空格形式 */
    fun displayName(key: String, zh: Boolean): String =
        if (zh) nameZh[key] ?: key else key.replace('_', ' ')
}
