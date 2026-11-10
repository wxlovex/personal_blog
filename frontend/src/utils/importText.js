// Decode locally; the original file is never uploaded to a separate endpoint.
export async function readArticleFile(file) {
  if (!/\.(txt|md)$/i.test(file.name)) throw new Error('请选择 .txt 或 .md 文件。')
  if (file.size > 1024 * 1024) throw new Error('文件不能超过 1 MB，正文最多 100,000 字。')
  const bytes = new Uint8Array(await file.arrayBuffer())
  let content
  try {
    if (bytes[0] === 0xff && bytes[1] === 0xfe) content = new TextDecoder('utf-16le', { fatal: true }).decode(bytes)
    else if (bytes[0] === 0xfe && bytes[1] === 0xff) content = new TextDecoder('utf-16be', { fatal: true }).decode(bytes)
    else {
      try { content = new TextDecoder('utf-8', { fatal: true }).decode(bytes) }
      catch { content = new TextDecoder('gb18030', { fatal: true }).decode(bytes) }
    }
  } catch { throw new Error('无法识别文件编码，请将文件另存为 UTF-8 后重试。') }
  if (content.includes('\0')) throw new Error('文件含有非文本内容，请选择纯文本文件。')
  if (!content.trim()) throw new Error('文件内容为空，请选择有正文的文件。')
  if (content.length > 100000) throw new Error('文件正文超过 100,000 字，请缩短后再导入。')
  return content
}
