package com.example

import com.example.data.repository.FRIENDLY_OVERLOAD_ERROR
import com.example.data.repository.StudyRepository
import com.example.model.EducationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class StudyModelsUnitTest {

    @Test
    fun `education level restores safely with default fallback`() {
        assertEquals(EducationLevel.PRIMARY, EducationLevel.fromStored("PRIMARY"))
        assertEquals(EducationLevel.MIDDLE_SCHOOL, EducationLevel.fromStored("MIDDLE_SCHOOL"))
        assertEquals(EducationLevel.HIGH_SCHOOL, EducationLevel.fromStored("HIGH_SCHOOL"))
        assertEquals(EducationLevel.UNIVERSITY, EducationLevel.fromStored("UNIVERSITY"))
        assertEquals(EducationLevel.HIGH_SCHOOL, EducationLevel.fromStored("UNKNOWN"))
    }

    @Test
    fun `503 high demand error maps to friendly Vietnamese overload message`() {
        val error503 = RuntimeException("HTTP 503: The model is currently experiencing high demand.")
        assertTrue(StudyRepository.isTransientOverloadError(error503))
        assertEquals(FRIENDLY_OVERLOAD_ERROR, StudyRepository.toFriendlyVietnameseError(error503))
    }

    @Test
    fun `network io error maps to friendly Vietnamese network message`() {
        val networkError = IOException("Unable to resolve host")
        assertEquals(
            "Kết nối mạng đang chập chờn. Bạn kiểm tra lại Wi-Fi/4G rồi bấm Thử lại nhé!",
            StudyRepository.toFriendlyVietnameseError(networkError)
        )
    }
}
