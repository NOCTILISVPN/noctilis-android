package net.noctilis.app

import android.content.pm.PackageManager

/**
 * Приложения, которые по умолчанию идут МИМО туннеля (решение Андрея 22.09.2026):
 * MAX, Госуслуги, банки, навигация, VK, Rutube. Они либо ловят VPN (MAX), либо не
 * работают через зарубежный адрес (банки, госуслуги). Список — имена пакетов; те, что не
 * установлены, просто не показываются. Имена пакетов сверять по Google Play / RuStore,
 * если приложение не исключилось — проверить имя пакета первым делом.
 */
object DefaultExclusions {
    val packages: List<String> = listOf(
        // мессенджеры и соцсети
        "ru.oneme.app",                          // MAX (сверено: бэкенд oneme.ru)
        "com.vkontakte.android",                 // VK
        "com.vk.vkvideo",                        // VK Видео
        "ru.ok.android",                         // Одноклассники
        "ru.mail.mailapp",                       // Почта Mail.ru
        // госуслуги
        "ru.rostel",                             // Госуслуги
        "ru.gosuslugi.pos",                      // Госуслуги.Дом / прочие
        "ru.mos.app",                            // Моя Москва
        "ru.nalog.lkfl",                         // Налоги ФЛ
        // банки и платежи
        "ru.sberbankmobile",                     // Сбербанк
        "ru.sberbank.spasibo",
        "com.idamob.tinkoff.android",            // Т-Банк
        "ru.alfabank.mobile.android",            // Альфа-Банк
        "ru.vtb24.mobilebanking.android",        // ВТБ
        "ru.gazprombank.android.mobilebank.app", // Газпромбанк
        "ru.raiffeisennews",                     // Райффайзен
        "ru.sovcombank.halva.mobile",            // Халва / Совкомбанк
        "ru.psbank.mobile",                      // ПСБ
        "ru.rosbank.android",                    // Росбанк
        "ru.mkb.mobile",                         // МКБ
        "ru.uralsib.mobile",                     // Уралсиб
        "ru.pochtabank.mobile",                  // Почта Банк
        "ru.rshb.mbank",                         // Россельхозбанк
        "ru.ozon.app.android",                   // Ozon (и Ozon Банк)
        "ru.yoo.money",                          // ЮMoney
        "ru.nspk.sbp.mobile",                    // СБПэй
        "ru.nspk.mirpay",                        // Mir Pay
        // навигация и такси
        "ru.yandex.yandexmaps",                  // Яндекс Карты
        "ru.yandex.yandexnavi",                  // Яндекс Навигатор
        "ru.dublgis.dgismobile",                 // 2ГИС
        "ru.yandex.taxi",                        // Яндекс Go
        "com.citymobil",                         // Ситимобил
        // видео
        "ru.rutube.app",                         // Rutube
        "ru.kinopoisk",                          // Кинопоиск
        "ru.ivi.client",                         // ИВИ
        "ru.mts.mtstv",                          // KION
        // остальное бытовое
        "com.wildberries.ru",                    // Wildberries
        "ru.beru.android",                       // Яндекс Маркет
        "com.avito.android",                     // Авито
    )

    /** Установленные на телефоне пакеты из списка по умолчанию. */
    fun installed(pm: PackageManager): Set<String> =
        packages.filter { p ->
            try { pm.getApplicationInfo(p, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
        }.toSet()
}
