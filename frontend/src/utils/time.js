// The API returns UTC LocalDateTime values without an offset.
export function utcTimestamp(value) {
  return value && !/(Z|[+-]\d{2}:\d{2})$/i.test(value) ? `${value}Z` : value
}

export function formatTime(value) {
  if (!value) return ''
  const timestamp = utcTimestamp(value)
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function formatDate(value) {
  return formatTime(value).slice(0, 10).replaceAll('-', '.')
}
