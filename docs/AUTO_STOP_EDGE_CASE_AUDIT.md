# Automatic trip ending audit

Baseline: cc091690b710e5a4a3bd275d6ac335e1f6cb2b4d. Branch: audit/auto-stop-edge-cases.

Source audit and automated regression plan. Physical-device validation remains separate.

| Priority / failure | Likelihood / severity | Root cause and safeguards | Coverage / disposition |
|---|---|---|---|
| P1 GPS silence leaves trip active | Device dependent / high | `LocationTrackingService.evaluateStationaryAutoStop` ran only from `locationCallback`. | Fixed: existing service ticker requests a bounded fresh location at/after the monotonic deadline and rechecks every 60s only while eligible. No fix means no stop. `missingCallbacksCanBeResolvedByFreshRecheck` tests the evidence sequence; service recheck requires device validation. |
| P1 timer restarts on process recovery | Moderate / high | `StationaryAutoStopMonitor` stored anchors and elapsed time only in memory; service reset on recovery. | Fixed: DataStore holds trip-bound stationary evidence and boot count. Restore accepts only same trip and same known boot; requires a new accurate fix to stop. `persistedEvidenceSurvivesProcessDeathWithinSameBoot` and reboot tests cover the monitor; service integration remains device dependent. |
| P2 inaccurate drift resets timeout or adds miles | Moderate / medium | Monitor accepted 75m accuracy, mileage filter only 35m, and 15m GPS noise could be admitted. | Fixed: aligned 35m threshold; low-speed displacement below combined accuracy radius is rejected. Regression tests cover both. |
| P2 malformed GPS evidence | Low / medium | NaN comparisons bypass accuracy and displacement checks. | Fixed finite/range/nonnegative checks; regression sequence added. |
| P2 delayed debounce targets current trip | Low / high | `TripDebounceWorker` input held a trigger but no trip ID. | Fixed: request carries trip ID, checks active ID before drain/finalization, and service validates ID before stop. Stale-worker regression added. |
| P2 manual trip activity debounce | Moderate / medium | Activity debounce worker had no method guard. | Fixed: worker only acts on `AUTO_DETECTED`; manual regression added. |
| P2 timeout/reentry race | Low / high | Worker checked vehicle state once; service stop had no session fence. | Fixed: service rechecks trip ID, auto method, vehicle state, and Bluetooth pending state before debounce stop. A persisted, trip-bound `IN_VEHICLE ENTER` marker vetoes a stale stationary timeout. Physical-device boundary test still needed. |
| P2 movement later in same GPS batch | Low / high | `locations.any` stopped evaluation on the first due stationary fix, potentially ignoring a later movement fix in that batch. | Fixed: monitor consumes the ordered batch and can request stop only on its final fix; regression sequence added. |
| P3 long traffic stop | Expected / medium | Ten minutes without meaningful speed/displacement ends a confirmed automatic trip even during congestion. STILL_ENTER itself is ignored. | Preserve ten-minute requirement; documented tradeoff. |
| P3 reboot and force-stop | Platform dependent / high | BootReceiver marks RECOVERY_REQUIRED and retains trip/inbox. Force-stop blocks automatic execution until Android permits restart. | BootReceiverRebootRecoveryTest; physical-device checks required. |
| P3 simultaneous stop / GPS drain | Low / high | Atomic isStopping, GpsAdmissionBarrier, Room inbox drain, coordinator mutex, lifecycle and insert-ignore protect normal service finalization. | Queue stress/transaction and concurrent-finalization tests exist; exhaustive signal matrix not yet run. |

## Traced paths

AutoDriveReceiver IN_VEHICLE ENTER sets state and invokes AutoDriveDecisionEngine: start candidate, recover existing trip, or cancel debounce. EXIT sets false and schedules four-minute work. STILL ENTER returns Ignore. Selected Bluetooth disconnect schedules the same debounce; reconnect sets vehicle state and cancels work. Service location callbacks enqueue GPS then evaluate the stationary monitor only for confirmed AUTO_DETECTED trips. Manual ACTION_STOP and worker ACTION_STOP enter the service's atomic stop gate. It removes callbacks, closes admission, drains accepted inbox work, then finalizes through TripFinalizationCoordinator. Inactive-worker recovery drains Room before finalization. Boot reconciliation retains unfinished data for recovery.

Coordinator rejects pending GPS work and discards persisted distance below 200m. No database schema, trip data, signing, Sheets or UI changes were made.

## Watchdog behavior and limitations

GPS silence is not proof of stationary activity. The service persists evidence only after two accurate observations at the same stationary anchor. The existing foreground service ticker notices a due monotonic deadline, requests one fresh high-accuracy fix with a 15-second timeout, and requires the fix's own monotonic timestamp to be at or beyond the deadline. An inaccurate, stale, unavailable, or moving fix cannot stop the trip. A same-trip `IN_VEHICLE ENTER` after the evidence vetoes the stop. Rechecks are spaced at least 60 seconds apart while eligible; there is no new service or WorkManager timing assumption. Same-boot service restart restores the evidence; reboot or unknown boot identity starts a new countdown from fresh observations. If Android force-stops the app, refuses to restart the service, revokes location, or cannot obtain a fresh fix, automatic ending is deferred. Preserved Room mileage can be recovered after execution resumes. Ten-minute stationary traffic remains the explicit tradeoff.

## Automated scenario matrix

| # | Scenario | Deterministic coverage |
|---|---|---|
| 1 | Regular stationary ten minutes | `StationaryAutoStopMonitorTest.tenMinutesStationary_requestsStop` |
| 2 | Missing callbacks then fresh accurate recheck | `missingCallbacksCanBeResolvedByFreshRecheck`; Android location request integration requires device validation |
| 3 | GPS drift and inaccurate fixes | `inaccurateDriftCannotResetStationaryCountdown`, `stationaryAccuracyRadiusDoesNotAccumulateFalseMileage` |
| 4 | Movement before deadline | `meaningfulMovement_resetsTenMinuteTimer` |
| 5 | Stop and resume at boundary | `movementAtExactTimeoutWinsOverStationaryStop`, `laterMovementInSameBatchPreventsStop`, `vehicleReentryAfterStationaryEvidenceVetoesStop`, `doWork_whenVehicleMotionResumed_returnsSuccessWithoutFinalizing`; exact service race requires device validation |
| 6 | Bluetooth disconnect and reconnect | `VehicleBluetoothEventHandlerTest.selectedCarDisconnect_startsSafeStopSignal`, `selectedCarReconnect_cancelsPendingStop` |
| 7 | Simultaneous stop triggers | `StationaryAutoStopMonitorTest.stopIsRequestedOnlyOnceUntilReset`; coordinator and admission gate tests cover idempotence, signal interleaving requires device validation |
| 8 | Process death during countdown | `StationaryEvidencePersistenceTest.serviceRecreationRetainsTripBoundStationaryEvidence`, `persistedEvidenceSurvivesProcessDeathWithinSameBoot`, `LocationTrackingServiceNullIntentRecoveryTest` |
| 9 | Reboot with unfinished trip | `rebootOrClockRegressionCannotRestoreOldElapsedTime`, `BootReceiverRebootRecoveryTest` |
| 10 | Manual trip immunity | `debounceWorkerNeverStopsManualTrip`, service stationary guard |
| 11 | No duplicate finalization | `discardedTripRetry_returnsNull_andPreservesDiscardedState`, coordinator tests |
| 12 | Concurrent GPS admission and stop | `GpsLocationQueueTest.serviceStop_racingWithIncomingCallback_doesNotLoseAcceptedFix` |
| 13 | Clock changes and monotonic time | `GpsInboxProcessorTransactionTest.wallClockBackwardJump_withMonotonicNanos_processesCorrectlyWithoutSpike`, monitor restore test |
| 14 | Background scheduling delay | `staleWorkerForPreviousTripCannotStopCurrentTrip`, worker recovery drain test |
| 15 | Under-200m discard | `AutomaticDriveDetectionTest.test02_driveNeverReaching200m_isDiscardedAsFalseTrip`, `test02b_exactDisplacementBoundaries_enforces200MetersPrecisely` |

## Verification and physical-device work

Device checks must use synthetic trips: background GPS withdrawal, tunnels, permission revocation, process kill, reboot, delayed workers, reconnect at deadline and long congestion. Never test deletion against real history. The matrix maps existing and new tests, but does not claim the Android location recheck or exact transition races have been verified on hardware.

The original repository omitted `gradle-wrapper.jar` (not gitignored). It is now restored from Gradle's `v8.10.2` tag; SHA-256 `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046` matches `services.gradle.org/distributions/gradle-8.10.2-wrapper.jar.sha256`. The distribution ZIP checksum is pinned to `31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26`. CI validates the wrapper before building.
