/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.revoman.integration.pokemon;

import static com.google.common.truth.Truth.assertThat;
import static com.salesforce.revoman.input.config.HookConfig.post;
import static com.salesforce.revoman.input.config.HookConfig.pre;
import static com.salesforce.revoman.input.config.StepPick.PostTxnStepPick.afterAllStepsContainingHeader;
import static com.salesforce.revoman.input.config.StepPick.PostTxnStepPick.afterStepName;
import static com.salesforce.revoman.input.config.StepPick.PreTxnStepPick.beforeAllStepsContainingHeader;
import static com.salesforce.revoman.input.config.StepPick.PreTxnStepPick.beforeStepName;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

import com.salesforce.revoman.ReVoman;
import com.salesforce.revoman.input.config.HookConfig.Hook.PostHook;
import com.salesforce.revoman.input.config.HookConfig.Hook.PreHook;
import com.salesforce.revoman.input.config.Kick;
import com.salesforce.revoman.output.Rundown;
import com.salesforce.revoman.output.report.Step;
import com.salesforce.revoman.output.report.StepReport;
import com.salesforce.revoman.output.report.TxnInfo;
import java.util.Map;
import org.http4k.core.Request;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PokemonTest {

  private static final String PM_COLLECTION_PATH =
      "pm-templates/pokemon/pokemon.postman_collection.json";
  private static final String PM_ENVIRONMENT_PATH =
      "pm-templates/pokemon/pokemon.postman_environment.json";
  private static final int LIMIT = 3;
  private static final int OFFSET = 0;
  private static final Logger LOGGER = LoggerFactory.getLogger(PokemonTest.class);

  @Test
  void pokemon() {
    final var newLimit = 1;
    final var dynamicEnvironment =
        Map.of(
            "offset", String.valueOf(OFFSET),
            "limit", String.valueOf(LIMIT));
    //noinspection Convert2Lambda
    final var preLogHook =
        Mockito.spy(
            new PreHook() {
              @Override
              public void accept(
                  @NotNull Step currentStep,
                  @NotNull TxnInfo<Request> requestInfo,
                  @NotNull Rundown rundown) {
                LOGGER.info("Picked `preLogHook` for stepName: {}", currentStep);
              }
            });
    //noinspection Convert2Lambda
    final var postLogHook =
        Mockito.spy(
            new PostHook() {
              @Override
              public void accept(@NotNull StepReport currentStepReport, @NotNull Rundown rundown) {
                LOGGER.info(
                    "Picked `postLogHook` for stepName: {}", currentStepReport.step.displayName);
              }
            });
    //noinspection Convert2Lambda
    final var preHook =
        Mockito.spy(
            new PreHook() {
              @Override
              public void accept(
                  @NotNull Step currentStep,
                  @NotNull TxnInfo<Request> requestInfo,
                  @NotNull Rundown rundown) {
                rundown.mutableEnv.set("limit", String.valueOf(newLimit));
              }
            });
    //noinspection Convert2Lambda
    final var postHook =
        Mockito.spy(
            new PostHook() {
              @Override
              public void accept(@NotNull StepReport ignore2, @NotNull Rundown rundown) {
                assertThat(rundown.mutableEnv).containsEntry("limit", String.valueOf(newLimit));
                assertThat(rundown.mutableEnv).containsEntry("pokemonName", "bulbasaur");
              }
            });
    final var pokeRundown =
        ReVoman.revUp(
            Kick.configure()
                .templatePath(PM_COLLECTION_PATH)
                .environmentPath(PM_ENVIRONMENT_PATH)
                .hooks(
                    pre(beforeStepName("all-pokemon"), preHook),
                    post(afterStepName("all-pokemon"), postHook),
                    pre(beforeAllStepsContainingHeader("preLog"), preLogHook),
                    post(afterAllStepsContainingHeader("postLog"), postLogHook))
                .dynamicEnvironment(dynamicEnvironment)
                .haltOnAnyFailure(true)
                .off());

    Mockito.verify(preHook, times(1)).accept(any(), any(), any());
    Mockito.verify(postHook, times(1)).accept(any(), any());
    Mockito.verify(preLogHook, times(1)).accept(any(), any(), any());
    Mockito.verify(postLogHook, times(1)).accept(any(), any());
    assertThat(pokeRundown.stepReports).hasSize(5);
    assertThat(pokeRundown.mutableEnv)
        .containsExactlyEntriesIn(
            Map.of(
                "offset", String.valueOf(OFFSET),
                "limit", String.valueOf(newLimit),
                "baseUrl", "https://pokeapi.co/api/v2",
                "id", "1",
                "pokemonName", "bulbasaur",
                "color", "black",
                "gender", "female",
                "ability", "stench",
                "nature", "hardy"));
  }
}
