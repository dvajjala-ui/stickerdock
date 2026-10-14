package app.stickerdock.duo

import org.junit.Assert.*
import org.junit.Test

class CatalogMissingObjectTest {
    @Test fun recognizesSupabaseMissingIndexWithoutHidingOtherErrors() {
        assertTrue(CommunityCatalog.isMissingObjectResponse(
            """{"statusCode":"404","error":"not_found","message":"Object not found","code":"NoSuchKey"}""".toByteArray()))
        assertFalse(CommunityCatalog.isMissingObjectResponse(
            """{"statusCode":"400","message":"Invalid API key"}""".toByteArray()))
        assertFalse(CommunityCatalog.isMissingObjectResponse("gateway failed".toByteArray()))
    }
}
