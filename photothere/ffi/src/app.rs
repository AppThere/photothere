//! What is PhotoCraft's own: the session, how methods reach it, and the composite renderer.

use photocraft_automation::Headless;
use serde_json::{Value, json};

use crate::Rendered;

pub(crate) struct Inner {
    h: Headless,
}

/// A session with a new default document. The person runs the app on their own machine, so file
/// access is the local CLI's (`trusted_local`), not a remote automation client's.
pub(crate) fn new() -> Inner {
    let mut h = Headless::trusted_local();
    // A failure leaves the session with no document; File › New still works.
    let _ = h.handle("doc.new", json!({}));
    Inner { h }
}

/// Every method of the headless JSON-lines server (`engine.execute`, `engine.commands`,
/// `session.list`, `doc.open`, `doc.save`, `doc.inspect`, `doc.render`, `batch`, …).
pub(crate) fn call(inner: &mut Inner, method: &str, params: Value) -> Result<Value, String> {
    inner.h.handle(method, params).map_err(|e| e.to_string())
}

/// Nothing is queued for the front end yet (the playbook's phase 6).
pub(crate) fn ui_requests(_inner: &mut Inner) -> Value {
    json!([])
}

/// `documents`: one page per open document, the active one current.
pub(crate) fn surface(inner: &mut Inner) -> Value {
    let s = &inner.h.session;
    json!({"kind": "documents", "pages": s.documents().len(), "current": s.active_index()})
}

/// Document `page`'s composite at `scale` pixels per document pixel (at most 1:1), straight alpha.
pub(crate) fn render(inner: &mut Inner, page: u32, scale: f32, max_pixels: u64) -> Option<Rendered> {
    inner.h.sync_jobs();
    let docs = inner.h.session.documents();
    let doc = &docs.get(usize::try_from(page).ok()?)?.doc;
    let longest = doc.size.width.max(doc.size.height).max(1);
    let side = ((longest as f32) * scale.min(1.0)).round().max(1.0) as u32;
    let (w, h) = photocraft_compose::thumbnail_size(doc, side);
    if u64::from(w) * u64::from(h) > max_pixels {
        return None;
    }
    let img = photocraft_compose::thumbnail(doc, side);
    Some(Rendered { width: img.width, height: img.height, pages: u32::try_from(docs.len()).unwrap_or(u32::MAX), rgba: img.pixels })
}
