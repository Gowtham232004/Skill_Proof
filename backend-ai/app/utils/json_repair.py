import json
import logging
import re


def strip_markdown_fence(content: str) -> str:
    text = (content or "").strip()
    if not text.startswith("```"):
        return text

    parts = text.split("```")
    if len(parts) < 2:
        return text

    fenced = parts[1]
    if fenced.startswith("json"):
        fenced = fenced[4:]
    return fenced.strip()


def extract_json_object(text: str) -> str:
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end < start:
        raise ValueError("No JSON object found")
    return text[start : end + 1]


def normalize_template_literal_fields(raw: str, fields: tuple[str, ...]) -> str:
    keys = "|".join(re.escape(field) for field in fields)
    pattern = re.compile(rf'("(?:{keys})"\s*:\s*)`(.*?)`', re.DOTALL)

    def _replace(match: re.Match[str]) -> str:
        prefix = match.group(1)
        value = match.group(2)
        return prefix + json.dumps(value)

    return pattern.sub(_replace, raw)


def repair_json_strings(raw: str) -> str:
    chars: list[str] = []
    in_string = False
    escaped = False
    idx = 0
    size = len(raw)

    while idx < size:
        ch = raw[idx]
        if in_string:
            if escaped:
                chars.append(ch)
                escaped = False
                idx += 1
                continue

            if ch == "\\":
                chars.append(ch)
                escaped = True
                idx += 1
                continue

            if ch == '"':
                look = idx + 1
                while look < size and raw[look] in " \t\r\n":
                    look += 1
                next_char = raw[look] if look < size else ""
                if next_char in {",", "}", "]", ":", ""}:
                    chars.append(ch)
                    in_string = False
                else:
                    chars.append("\\\"")
                idx += 1
                continue

            if ch == "\n":
                chars.append("\\n")
            elif ch == "\r":
                chars.append("\\r")
            elif ch == "\t":
                chars.append("\\t")
            elif ord(ch) < 0x20 or ord(ch) == 0x7F:
                chars.append(f"\\u{ord(ch):04x}")
            else:
                chars.append(ch)
            idx += 1
            continue

        chars.append(ch)
        if ch == '"':
            in_string = True
            escaped = False
        idx += 1

    repaired = "".join(chars)
    repaired = re.sub(r"[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]", "", repaired)
    repaired = re.sub(r",\s*}", "}", repaired)
    repaired = re.sub(r",\s*]", "]", repaired)
    return repaired


def parse_loose_json(content: str, logger: logging.Logger, context: str, fields_with_backticks: tuple[str, ...] = ()) -> dict:
    text = strip_markdown_fence(content)
    candidate = extract_json_object(text)
    if fields_with_backticks:
        candidate = normalize_template_literal_fields(candidate, fields_with_backticks)
    candidate = repair_json_strings(candidate)

    try:
        return json.loads(candidate)
    except json.JSONDecodeError as exc:
        preview = candidate[:600].replace("\n", "\\n")
        logger.error("%s JSON decode error: %s | preview=%s", context, exc, preview)
        raise ValueError(f"Invalid JSON from model: {exc}") from exc