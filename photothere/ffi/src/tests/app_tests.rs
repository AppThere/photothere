//! PhotoCraft's own: a session starts with a document, and it renders.

use super::*;

#[test]
fn a_new_session_has_a_document_that_renders() {
    let s = loki_session_new();
    assert_eq!(call(s, "loki.surface", None)["result"]["pages"], 1);
    let (mut w, mut h, mut n) = (0, 0, 0);
    // SAFETY: valid session and out-pointers.
    let px = unsafe { loki_render_page(s, 0, 0.1, &mut w, &mut h, &mut n) };
    assert!(!px.is_null() && w > 0 && h > 0 && n == 1);
    unsafe { loki_buffer_free(px, w as usize * h as usize * 4) };
    unsafe { loki_session_free(s) };
}
