# Automatic trip ending audit

Baseline: cc091690b710e5a4a3bd275d6ac335e1f6cb2b4d. Branch: audit/auto-stop-edge-cases.

This is an initial source audit, not a completed device validation or a claim that every requested scenario passes.

| Priority / failure | Likelihood / severity | Root cause and safeguards | Coverage / disposition |
|---|---|---|---|
| P1 GPS silence leaves trip active | Device dependent / high | LocationTrackingService.evaluateStationaryAutoStop runs only from locationCallback. No independent deadline evaluation. | Missing-callback watchdog remains unresolved. |
| P1 timer restarts on process recovery | Moderate / high | StationaryAutoStopMonitor stores anchors and elapsed time only in memory; service resets on recovery. Room preserves mileage but not stationary evidence. | Existing service recovery tests preserve trip; stationary continuity unverified. |
| P2 inaccurate drift resets timeout | Moderate / medium | Monitor accepts accuracy up to 75m but resets on 60m displacement; mileage filter rejects accuracy over 35m. Stop evidence and mileage admission differ. | Existing accuracy test only exercises 100m rejection; 35–75m drift requires additional sequences. |
| P2 malformed GPS evidence | Low / medium | NaN comparisons bypass accuracy and displacement checks. | Fixed finite/range/nonnegative checks; regression sequence added. |
| P2 delayed debounce targets current trip | Low / high | TripDebounceWorker input has trigger but no trip ID. It reads current active trip after delay; cancellation alone is not an execution fence. | Requires trip-bound requests and race tests before changing stop contracts. |
| P2 manual trip activity debounce | Moderate / medium | Bluetooth handler rejects manual trips, stationary service guard rejects manual trips; activity debounce worker has no equivalent method guard. | Manual immunity for stationary fallback exists; wider activity-stop policy unresolved. |
| P2 timeout/reentry race | Low / high | Worker checks vehicle state once before service stop; service ACTION_STOP has no generation fence. | Existing worker tests do not prove boundary linearization. |
| P3 long traffic stop | Expected / medium | Ten minutes without meaningful speed/displacement ends a confirmed automatic trip even during congestion. STILL_ENTER itself is ignored. | Preserve ten-minute requirement; documented tradeoff. |
| P3 reboot and force-stop | Platform dependent / high | BootReceiver marks RECOVERY_REQUIRED and retains trip/inbox. Force-stop blocks automatic execution until Android permits restart. | BootReceiverRebootRecoveryTest; physical-device checks required. |
| P3 simultaneous stop / GPS drain | Low / high | Atomic isStopping, GpsAdmissionBarrier, Room inbox drain, coordinator mutex, lifecycle and insert-ignore protect normal service finalization. | Queue stress/transaction and concurrent-finalization tests exist; exhaustive signal matrix not yet run. |

## Traced paths

AutoDriveReceiver IN_VEHICLE ENTER sets state and invokes AutoDriveDecisionEngine: start candidate, recover existing trip, or cancel debounce. EXIT sets false and schedules four-minute work. STILL ENTER returns Ignore. Selected Bluetooth disconnect schedules the same debounce; reconnect sets vehicle state and cancels work. Service location callbacks enqueue GPS then evaluate the stationary monitor only for confirmed AUTO_DETECTED trips. Manual ACTION_STOP and worker ACTION_STOP enter the service's atomic stop gate. It removes callbacks, closes admission, drains accepted inbox work, then finalizes through TripFinalizationCoordinator. Inactive-worker recovery drains Room before finalization. Boot reconciliation retains unfinished data for recovery.

Coordinator rejects pending GPS work and discards persisted distance below 200m. No database schema, trip data, signing, Sheets or UI changes were made.

## Watchdog decision

GPS silence is not proof of stationary activity: a tunnel or garage can suppress callbacks while travel continues. A durable watchdog should persist trip ID, boot identity, last reliable movement and stationary evidence, request fresh location when execution is allowed, and revalidate activity/reconnection and GPS admission before issuing a trip-bound stop. WorkManager is eventual scheduling, never an exact ten-minute clock. Reboot invalidates elapsed-time comparisons. This implementation has not been added; the missing-callback P1 remains open rather than risking premature finalization.

## Validation and outstanding work

Added invalid-location regression sequence. Existing tests cover regular stationary timeout, movement reset, inaccurate fixes, one-shot requests, queue drain, reboot preservation and concurrent finalization. The complete requested 15-scenario deterministic matrix is not yet implemented. Device checks must use synthetic trips: background GPS withdrawal, tunnels, permission revocation, process kill, reboot, delayed workers, reconnect at deadline and long congestion. Never test deletion against real history.

Local requested Gradle invocation failed before compilation because gradle-wrapper.jar is absent. CI generates the wrapper. Audit branch CI now runs the requested command and repeats it with --rerun-tasks. Results must be checked before claiming success.
