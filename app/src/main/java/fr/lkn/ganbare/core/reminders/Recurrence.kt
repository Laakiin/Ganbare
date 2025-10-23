package fr.lkn.ganbare.core.reminders

enum class Recurrence {
    NONE, DAILY, WEEKLY, MONTHLY
}

data class RecurrenceMapping(
    val p1: Recurrence,
    val p2: Recurrence,
    val p3: Recurrence,
    val p4: Recurrence
) {
    /** Accepte priorité 0..3 ou 1..4 ; normalise en bande P1..P4 */
    fun forPriority(priority: Int): Recurrence {
        val band = when {
            priority in 0..3 -> priority + 1  // 0->P1, 1->P2, 2->P3, 3->P4
            priority < 1 -> 1
            priority > 4 -> 4
            else -> priority
        }
        return when (band) {
            1 -> p1
            2 -> p2
            3 -> p3
            else -> p4
        }
    }

    companion object {
        /** Défaut cohérent avec le mapping précédent : P4=DAILY, P3=WEEKLY, P2=MONTHLY, P1=NONE */
        fun default() = RecurrenceMapping(
            p1 = Recurrence.NONE,
            p2 = Recurrence.MONTHLY,
            p3 = Recurrence.WEEKLY,
            p4 = Recurrence.DAILY
        )
    }
}
