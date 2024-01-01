package com.salesforce.revoman.input.config

import com.salesforce.revoman.input.config.HookConfig.Hook.PostHook
import com.salesforce.revoman.input.config.HookConfig.Hook.PreHook
import com.salesforce.revoman.input.config.StepPick.PostTxnStepPick
import com.salesforce.revoman.input.config.StepPick.PreTxnStepPick
import com.salesforce.revoman.output.Rundown
import com.salesforce.revoman.output.report.Step
import com.salesforce.revoman.output.report.StepReport
import com.salesforce.revoman.output.report.TxInfo
import org.http4k.core.Request

data class HookConfig private constructor(val pick: StepPick, val hook: Hook) {
  sealed interface Hook {
    fun interface PreHook : Hook {
      @Throws(Throwable::class)
      fun accept(currentStep: Step, requestInfo: TxInfo<Request>, rundown: Rundown)
    }

    fun interface PostHook : Hook {
      @Throws(Throwable::class) fun accept(currentStepReport: StepReport, rundown: Rundown)
    }
  }

  companion object {
    @JvmStatic fun pre(pick: PreTxnStepPick, hook: PreHook): HookConfig = HookConfig(pick, hook)

    @JvmStatic fun post(pick: PostTxnStepPick, hook: PostHook): HookConfig = HookConfig(pick, hook)
  }
}
