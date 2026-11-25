# Post body HTML contract

`POST /api/v1/posts` and `PUT /api/v1/posts/{rowId}` accept an HTML fragment in `content`. Plain text can also be sent; HTML-sensitive characters are escaped in the returned fragment. Encode literal angle brackets as HTML entities if they would otherwise be parsed as tags.

| Formatting | Allowed elements |
| --- | --- |
| Paragraphs, breaks, and headings | `p`, `br`, `h1`, `h2`, `h3` |
| Bold, italic, underline, and strikethrough | `b`, `strong`, `i`, `em`, `u`, `s` |
| Inline code and quotes | `code`, `blockquote` |
| Numbered and bulleted lists | `ol`, `ul`, `li` |

Only `ol[start]` and `li[value]` are retained, each accepting an optional minus sign and up to ten ASCII digits. The server strips other attributes (including Lexical's `class` and `style`), inline anchors, media, and unsupported markup. Use the separate `links` field for clickable external links. Style the rich-text container in the frontend.

The API filters new writes and all `PostResponse.content` values, including legacy rows in moderation, list, search, and history responses. An edit with missing or null `content` inherits the sanitized source body. Existing database rows are not rewritten. Render the returned fragment as the contents of an HTML element, such as a `div`; it is not an HTML attribute, CSS, JavaScript, or URL value.

New and replacement bodies must contain text after sanitization. A sanitized body over 65,535 UTF-8 bytes returns `400`, including on an edit that inherits a large legacy body. Submit a shorter replacement to edit such a post.
