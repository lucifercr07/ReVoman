/*******************************************************************************
 * Copyright (c) 2023, Salesforce, Inc.
 *  All rights reserved.
 *  SPDX-License-Identifier: BSD-3-Clause
 *  For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 ******************************************************************************/
package org.revcloud.revoman.internal.postman

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.revcloud.revoman.TEST_RESOURCES_PATH
import org.revcloud.revoman.internal.postman.state.EnvValue
import org.revcloud.revoman.internal.postman.state.Environment

class PostmanTest {

  @Test
  fun `unmarshall Env File with Regex and Dynamic variable`() {
    val epoch = System.currentTimeMillis().toString()
    val dummyDynamicVariableGenerator = { r: String -> if (r == "\$epoch") epoch else null }
    val actualEnv =
      regexReplace(
        "${TEST_RESOURCES_PATH}/env-with-regex.json",
        mutableMapOf("un" to "userName"),
        emptyMap(),
        dummyDynamicVariableGenerator
      )
    val expectedEnv =
      Environment(
        values = listOf(EnvValue(key = "userName", value = "user-$epoch@xyz.com", enabled = true))
      )
    actualEnv shouldBe expectedEnv
  }
}
