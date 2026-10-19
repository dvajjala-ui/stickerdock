# Optional original artwork worker

This is a private development worker, not a feature enabled in the shipped app
or scheduled Factory. Existing Groq captions and owned/licensed media processing
continue independently. No OpenAI key is required by this adapter. No provider
credentials belong in Android, Git, a brief, browser code or logs.

## Prepare without spending

From the repository root with the existing Python requirements installed:

```sh
python tools/original_artwork.py --brief tools/original-pack.example.json --output work/my-original-pack
```

This validates the six-reaction example, writes a canonical private brief and
journal, and returns `spending_disabled` with zero submissions. It does not call
an image provider. The CLI currently evaluates BFL's `flux-2-klein-4b`, not an
assumed free service. Model quality and live adapter behavior remain unverified.

## Operator-controlled trial

Only after a provider account/key and a specific trial spending limit have been
approved, configure `BFL_API_KEY` in the worker environment and invoke the same
command with `--allow-paid --max-submissions 6`. Never paste a key into command
history or a tracked file. Current provider pricing determines cost; a request
cap is not a currency budget. The cap is cumulative **per output directory**.
Creating another output directory starts a separate cap.

Each invocation submits **or** polls one reaction. Repeat only while its state is
`waiting` or `ready`. The CLI is deliberately not an unattended scheduler. A
durable reservation is written before POST; ambiguous failures become
`needs_attention`. Never delete that journal to retry: the request could have
been accepted and billed. Reconcile uncertain requests with the provider first.
The journal's `submitting` state after abrupt process death likewise requires
manual reconciliation, not a second submission.

`submission_limit` stops new requests. `rejected`, `duplicate_artwork` and
`needs_attention` require review. A polling/download failure can resume the same
saved job without a new POST. Credentials are sent only to the provider API;
signed delivery URLs receive no API key and are not printed. Keep the whole
working folder private because journals retain request identifiers and polling
URLs. Do not upload it as a CI artifact.

## Output and review

The output retains source PNGs, normalized 512×512 WebPs, checksums and the brief.
When all reactions complete, the next invocation returns `needs_review` and
creates `review-pack.zip`, compatible with StickerDock's v5 importer. Names,
reaction captions and emotion tags survive import. Nothing is published or added
to a user's library automatically.

Review expressions at chat size, useful/distinct reactions, character consistency,
backgrounds/edges, rights and audience suitability. Shared prompts are not a
guarantee of character consistency. This adapter adds transparent outside margins
but does **not** remove a generated opaque background. Captions remain metadata;
they are not painted on the artwork by this path. Repair/reject poor results before
using the existing reviewed-publication workflow. No live quality claim follows
from the mocked tests.

See [product decisions and evaluated sources](PRODUCT_REBUILD_2026-10-18.md).
