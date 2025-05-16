package com.example.eyeSeeYou.helpers

enum class Zones {
    LOW,
    CENTER,
    HIGH,
    RIGHT,
    LEFT,
    RIGHT_WALL,
    LEFT_WALL;

    companion object {
        fun describe(zones: Set<Zones>): String {
            val vertical = when {
                Zones.LOW in zones -> "in basso"
                Zones.HIGH in zones -> "in alto"
                Zones.CENTER in zones -> "davanti"
                else -> ""
            }
            val horizontal = when {
                Zones.LEFT_WALL in zones -> "muro a sinistra"
                Zones.RIGHT_WALL in zones -> "muro a destra"
                Zones.LEFT in zones -> "a sinistra"
                Zones.RIGHT in zones -> "a destra"
                else -> ""
            }

            return when {
                horizontal.contains("muro") -> "ostacolo $horizontal"
                vertical.isNotEmpty() && horizontal.isNotEmpty() -> "ostacolo $vertical $horizontal"
                vertical.isNotEmpty() -> "ostacolo $vertical"
                horizontal.isNotEmpty() -> "ostacolo $horizontal"
                else -> "ostacolo davanti"
            }
        }
    }
}