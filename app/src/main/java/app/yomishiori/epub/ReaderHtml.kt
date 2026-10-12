package app.yomishiori.epub

object ReaderHtml {
    private const val MARKER = "yomishiori-reader"

    private val script = """
        <script id="$MARKER" type="text/javascript">
        document.addEventListener('DOMContentLoaded', function () {
          var set = function (node, name, value) { node.style.setProperty(name, value, 'important'); };
          var els = document.querySelectorAll('img, svg');
          for (var i = 0; i !== els.length; i++) {
            var el = els[i];
            var parent = el.parentElement;
            if (parent) {
              if (parent.textContent.trim() === '') {
                set(parent, 'text-align', 'center');
                set(parent, 'text-indent', '0');
                set(el, 'display', 'block');
                set(el, 'margin-left', 'auto');
                set(el, 'margin-right', 'auto');
              }
            }
          }
          if (els.length === 1) {
            if (document.body.textContent.trim() === '') {
              var picture = els[0];
              var box = picture.parentElement;
              var blocks = document.body.querySelectorAll('p, div, br, hr');
              for (var j = 0; j !== blocks.length; j++) {
                var block = blocks[j];
                if (block !== box) {
                  if (!block.contains(picture)) { set(block, 'display', 'none'); }
                }
              }
              set(box, 'display', 'flex');
              set(box, 'align-items', 'center');
              set(box, 'justify-content', 'center');
              set(box, 'height', '88vh');
              set(box, 'margin', '0');
              set(box, 'padding', '0');
              set(picture, 'margin', '0');
              set(picture, 'max-width', '100%');
              set(picture, 'max-height', '88vh');
              if (picture.tagName.toLowerCase() === 'svg') {
                set(picture, 'width', '100%');
                set(picture, 'height', '88vh');
              } else {
                set(picture, 'width', 'auto');
                set(picture, 'height', 'auto');
                set(picture, 'object-fit', 'contain');
              }
            }
          }
        });
        </script>
    """.trimIndent()

    private val headEnd = Regex("</head\\s*>", RegexOption.IGNORE_CASE)
    private val bodyStart = Regex("<body[^>]*>", RegexOption.IGNORE_CASE)

    fun inject(html: String): String {
        if (html.contains(MARKER)) return html
        headEnd.find(html)?.let { return html.replaceRange(it.range.first, it.range.first, script) }
        bodyStart.find(html)?.let { return html.replaceRange(it.range.last + 1, it.range.last + 1, script) }
        return html
    }

    fun inject(bytes: ByteArray): ByteArray {
        val utf16 = bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) || (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()))
        if (utf16) return bytes
        val html = bytes.decodeToString()
        val out = inject(html)
        return if (out === html) bytes else out.encodeToByteArray()
    }

    fun isHtml(path: String): Boolean {
        val p = path.lowercase()
        return p.endsWith(".xhtml") || p.endsWith(".html") || p.endsWith(".htm")
    }
}
