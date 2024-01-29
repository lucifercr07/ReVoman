/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.revoman.output.report.failure

import com.salesforce.revoman.output.ExeType.HTTP_STATUS_UNSUCCESSFUL
import com.salesforce.revoman.output.report.TxnInfo
import org.http4k.core.Request
import org.http4k.core.Response

data class HttpStatusUnsuccessful(
  val requestInfo: TxnInfo<Request>,
  val responseInfo: TxnInfo<Response>,
) {
  val exeType = HTTP_STATUS_UNSUCCESSFUL
}
