package com.sesolibre.somnia

import com.sesolibre.somnia.questionnaires.Epworth
import com.sesolibre.somnia.questionnaires.StopBang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EpworthTest {

    @Test
    fun `suma simple de respuestas`() {
        assertEquals(0, Epworth.score(List(8) { 0 }))
        assertEquals(24, Epworth.score(List(8) { 3 }))
        assertEquals(11, Epworth.score(listOf(1, 2, 0, 3, 1, 2, 1, 1)))
    }

    @Test
    fun `rechaza numero incorrecto de respuestas`() {
        assertThrows(IllegalArgumentException::class.java) { Epworth.score(List(7) { 0 }) }
    }

    @Test
    fun `rechaza respuestas fuera de rango`() {
        assertThrows(IllegalArgumentException::class.java) {
            Epworth.score(listOf(0, 0, 0, 0, 0, 0, 0, 4))
        }
    }

    @Test
    fun `bandas de interpretacion en los cortes`() {
        assertEquals(Epworth.LEVEL_NORMAL, Epworth.riskLevel(0))
        assertEquals(Epworth.LEVEL_NORMAL, Epworth.riskLevel(7))
        assertEquals(Epworth.LEVEL_MILD, Epworth.riskLevel(8))
        assertEquals(Epworth.LEVEL_MILD, Epworth.riskLevel(9))
        assertEquals(Epworth.LEVEL_MODERATE, Epworth.riskLevel(10))
        assertEquals(Epworth.LEVEL_MODERATE, Epworth.riskLevel(15))
        assertEquals(Epworth.LEVEL_SEVERE, Epworth.riskLevel(16))
        assertEquals(Epworth.LEVEL_SEVERE, Epworth.riskLevel(24))
    }
}

class StopBangTest {

    @Test
    fun `puntaje cuenta los si`() {
        assertEquals(0, StopBang.score(List(8) { false }))
        assertEquals(8, StopBang.score(List(8) { true }))
        assertEquals(3, StopBang.score(listOf(true, false, true, false, true, false, false, false)))
    }

    @Test
    fun `bandas de riesgo en los cortes`() {
        assertEquals(StopBang.LEVEL_LOW, StopBang.riskLevel(2))
        assertEquals(StopBang.LEVEL_INTERMEDIATE, StopBang.riskLevel(3))
        assertEquals(StopBang.LEVEL_INTERMEDIATE, StopBang.riskLevel(4))
        assertEquals(StopBang.LEVEL_HIGH, StopBang.riskLevel(5))
    }

    @Test
    fun `prellenado desde perfil respeta umbrales`() {
        assertEquals(true, StopBang.bmiItem(36.0))
        assertEquals(false, StopBang.bmiItem(35.0)) // el corte es estrictamente mayor
        assertNull(StopBang.bmiItem(null))

        assertEquals(true, StopBang.ageItem(51))
        assertEquals(false, StopBang.ageItem(50))

        assertEquals(true, StopBang.neckItem(41))
        assertEquals(false, StopBang.neckItem(40))

        assertEquals(true, StopBang.genderItem("m"))
        assertEquals(false, StopBang.genderItem("f"))
        assertNull(StopBang.genderItem(null))
    }
}
