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

#[test]
fn a_rectangular_selection_outlines_as_its_four_sides_and_nothing_changes() {
    let s = loki_session_new();
    assert_eq!(call(s, "loki.selectionOutline", None)["result"]["segments"], serde_json::json!([]));
    let r = call(s, "engine.execute", Some(r#"{"command":"select.rect","params":{"x":10,"y":20,"width":100,"height":50}}"#));
    assert_eq!(r["ok"], true, "{r}");
    let before = call(s, "document.inspect", None)["result"]["history"].clone();
    let o = call(s, "loki.selectionOutline", Some(r#"{"step":1}"#))["result"].clone();
    let segs: Vec<f64> = o["segments"].as_array().unwrap().iter().map(|v| v.as_f64().unwrap()).collect();
    assert_eq!(segs.len(), 16, "{o}");
    assert!(segs.contains(&10.0) && segs.contains(&110.0) && segs.contains(&20.0) && segs.contains(&70.0), "{o}");
    // A read: no history step, no work path.
    assert_eq!(call(s, "document.inspect", None)["result"]["history"], before);
    // Hostile steps are clamped, never a crash.
    assert_eq!(call(s, "loki.selectionOutline", Some(r#"{"step":-5}"#))["ok"], true);
    assert_eq!(call(s, "loki.selectionOutline", Some(r#"{"step":1e30}"#))["ok"], true);
    unsafe { loki_session_free(s) };
}
