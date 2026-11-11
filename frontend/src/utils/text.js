// Format characters may be meaningful inside text, including emoji joined with ZWJ.
export function hasVisibleText(value) {
  return /[^\p{White_Space}\p{Cf}\p{Cc}]/u.test(value)
}
