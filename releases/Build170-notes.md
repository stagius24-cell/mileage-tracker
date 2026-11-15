# Build 170 (1.0.169)

Candidate expiry waits for admitted GPS batches to finish persistence, including a batch already taken by the ingestion worker. Persistence errors prevent discard even if the database pending count is zero.

Recovery now uses an explicit confirmed/retry-required/terminated result. Confirmed sessions and candidates preserved after a drain failure continue initialization; terminated candidates abort it.

Verification: 393 unit tests, zero failures or errors; debug APK built successfully. Added held-insertion, insertion-failure, confirmed recovery, and failed-drain recovery regression tests. Existing signing certificate preserved. No physical device was connected; real drive-and-park behavior was not tested.

The exit debounce is four minutes after an exit signal. The independent stationary fallback requires ten minutes of qualifying stationary evidence; parking does not universally guarantee a four-minute stop.
