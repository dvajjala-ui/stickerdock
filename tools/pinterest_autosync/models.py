from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class Candidate:
    key: str
    source: str
    source_ref: str
    pin_id: str | None
    pin_url: str | None
    kind: str
    media_url: str | None
    raw: dict[str, Any] = field(default_factory=dict)
    native: Any = field(default=None, repr=False, compare=False)

    @property
    def can_download(self) -> bool:
        return bool(self.media_url or self.native)
