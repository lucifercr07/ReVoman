/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.revoman.internal.exe

import arrow.core.Either
import arrow.core.Either.Right
import com.salesforce.revoman.input.config.HookConfig
import com.salesforce.revoman.input.config.HookConfig.Hook.PreHook
import com.salesforce.revoman.input.config.Kick
import com.salesforce.revoman.input.config.StepPick.PreTxnStepPick
import com.salesforce.revoman.internal.postman.pm
import com.salesforce.revoman.output.Rundown
import com.salesforce.revoman.output.report.ExeType.PRE_HOOK
import com.salesforce.revoman.output.report.Step
import com.salesforce.revoman.output.report.StepReport
import com.salesforce.revoman.output.report.TxInfo
import com.salesforce.revoman.output.report.failure.HookFailure.PreHookFailure
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.Request

internal fun preHookExe(
  currentStep: Step,
  kick: Kick,
  requestInfo: TxInfo<Request>,
  stepReports: List<StepReport>
): Either<PreHookFailure, Unit> =
  pickPreHooks(
      kick.preHooks(),
      currentStep,
      requestInfo,
      Rundown(stepReports, pm.environment, kick.haltOnAnyFailureExcept())
    )
    .asSequence()
    .map { preHook ->
      runChecked(currentStep, PRE_HOOK) {
          preHook.accept(
            currentStep,
            requestInfo,
            Rundown(stepReports, pm.environment, kick.haltOnAnyFailureExcept())
          )
        }
        .mapLeft { PreHookFailure(it, requestInfo) }
    }
    .firstOrNull { it.isLeft() } ?: Right(Unit)

private fun pickPreHooks(
  preHooks: List<HookConfig>,
  currentStep: Step,
  requestInfo: TxInfo<Request>,
  rundown: Rundown
): List<PreHook> =
  preHooks
    .asSequence()
    .filter { (it.pick as PreTxnStepPick).pick(currentStep, requestInfo, rundown) }
    .map { it.hook as PreHook }
    .toList()
    .also {
      if (it.isNotEmpty()) {
        logger.info { "$currentStep Picked Pre hook count : ${it.size}" }
        currentStep.preHookCount = it.size
      }
    }

private val logger = KotlinLogging.logger {}
