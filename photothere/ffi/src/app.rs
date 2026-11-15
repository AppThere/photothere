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
    if method == "loki.selectionOutline" {
        return Ok(selection_outline(inner, &params));
    }
    inner.h.handle(method, params).map_err(|e| e.to_string())
}

/// Most grid cells read for one outline (about 4 million): a larger document is read coarser.
const MAX_CELLS: u64 = 4 << 20;
/// Most segments returned: past this the outline is cut short (`truncated`).
const MAX_SEGMENTS: usize = 60_000;

/// The edges of the active document's selection, for marching ants: `segments` as flat
/// `[x0, y0, x1, y1, …]` in document pixels, where coverage crosses one half. The mask is read on a
/// grid of `step` pixels (`params.step`, 1..64; raised for a large document), so a canvas zoomed
/// out asks for a coarser outline. Reads only: the work path and history are untouched, unlike
/// `select.toWorkPath`.
fn selection_outline(inner: &mut Inner, params: &Value) -> Value {
    let empty = json!({"segments": [], "step": 1, "truncated": false});
    let docs = inner.h.session.documents();
    let Some(i) = inner.h.session.active_index() else { return empty };
    let Some(d) = docs.get(i) else { return empty };
    let Some(mask) = d.doc.selection.as_ref() else { return empty };
    let (w, h) = (u64::from(d.doc.size.width), u64::from(d.doc.size.height));
    if w == 0 || h == 0 {
        return empty;
    }
    let mut step = params.get("step").and_then(Value::as_u64).unwrap_or(1).clamp(1, 64);
    while (w.div_ceil(step)) * (h.div_ceil(step)) > MAX_CELLS && step < 4096 {
        step *= 2;
    }
    let (cols, rows) = (w.div_ceil(step) as usize, h.div_ceil(step) as usize);
    let half = (step / 2) as i32;
    let si = step as i32;
    let mut inside = vec![false; cols * rows];
    let mut row = vec![0f32; cols];
    for r in 0..rows {
        mask.sample_row_strided(r as i32 * si + half, half, si, 0, &mut row);
        for (c, v) in row.iter().enumerate() {
            if let Some(cell) = inside.get_mut(r * cols + c) {
                *cell = *v >= 0.5;
            }
        }
    }
    let at = |r: isize, c: isize| {
        r >= 0 && c >= 0 && (r as usize) < rows && (c as usize) < cols && inside.get(r as usize * cols + c as usize).copied().unwrap_or(false)
    };
    let mut segs: Vec<f64> = Vec::new();
    let mut truncated = false;
    let s = step as f64;
    // Horizontal edges (between a row and the one above), merged along x.
    'h: for r in 0..=rows as isize {
        let mut start: Option<isize> = None;
        for c in 0..=cols as isize {
            let edge = c < cols as isize && at(r - 1, c) != at(r, c);
            match (edge, start) {
                (true, None) => start = Some(c),
                (false, Some(c0)) => {
                    if segs.len() / 4 >= MAX_SEGMENTS {
                        truncated = true;
                        break 'h;
                    }
                    segs.extend([c0 as f64 * s, r as f64 * s, c as f64 * s, r as f64 * s]);
                    start = None;
                }
                _ => {}
            }
        }
    }
    // Vertical edges (between a column and the one to its left), merged along y.
    'v: for c in 0..=cols as isize {
        let mut start: Option<isize> = None;
        for r in 0..=rows as isize {
            let edge = r < rows as isize && at(r, c - 1) != at(r, c);
            match (edge, start) {
                (true, None) => start = Some(r),
                (false, Some(r0)) => {
                    if segs.len() / 4 >= MAX_SEGMENTS {
                        truncated = true;
                        break 'v;
                    }
                    segs.extend([c as f64 * s, r0 as f64 * s, c as f64 * s, r as f64 * s]);
                    start = None;
                }
                _ => {}
            }
        }
    }
    // Edges are in document pixels; the last column and row may overhang a size that is not a multiple of step.
    let (wf, hf) = (w as f64, h as f64);
    for (k, v) in segs.iter_mut().enumerate() {
        *v = v.min(if k % 2 == 0 { wf } else { hf });
    }
    json!({"segments": segs, "step": step, "truncated": truncated})
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
