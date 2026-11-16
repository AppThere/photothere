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

fn exec(s: *mut LokiSession, command: &str, params: serde_json::Value) -> serde_json::Value {
    let body = serde_json::json!({"command": command, "params": params}).to_string();
    let r = call(s, "engine.execute", Some(&body));
    assert_eq!(r["ok"], true, "{command}: {r}");
    r["result"].clone()
}

fn pixel(s: *mut LokiSession, x: f64, y: f64) -> Vec<f64> {
    exec(s, "document.sampleColor", serde_json::json!({"x": x, "y": y})).as_array().unwrap().iter().map(|v| v.as_f64().unwrap()).collect()
}

/// What the colour picker and the brush chips send: the next stroke is in that colour, at that size.
#[test]
fn the_picked_colour_and_brush_size_paint_the_next_stroke() {
    let s = loki_session_new();
    exec(s, "tools.setColors", serde_json::json!({"foreground": "#ff0000"}));
    exec(s, "tools.setBrush", serde_json::json!({"size": 40.0}));
    exec(s, "tools.setBrush", serde_json::json!({"hardness": 1.0}));
    exec(s, "tools.setBrush", serde_json::json!({"opacity": 1.0}));
    let b = exec(s, "brush.get", serde_json::json!({}));
    assert_eq!((b["size"].as_f64(), b["hardness"].as_f64()), (Some(40.0), Some(1.0)), "{b}");
    exec(s, "paint.stroke", serde_json::json!({"points": [[60, 60, 1, 0, 0, 0, 0], [160, 60, 1, 0, 0, 0, 50]]}));
    let on = pixel(s, 110.0, 60.0);
    assert!(on[0] > 0.9 && on[1] < 0.1 && on[2] < 0.1, "the stroke's middle is not red: {on:?}");
    // 40 px wide: 15 px off the line is paint, 30 px off is not.
    assert!(pixel(s, 110.0, 75.0)[1] < 0.1, "15 px from the line should be painted");
    assert_ne!(pixel(s, 110.0, 90.0), on, "30 px from the line should not be painted");
    // The background well, and the presets the Brush chip lists.
    exec(s, "tools.setColors", serde_json::json!({"background": "#00ff00"}));
    let session = exec(s, "session.inspect", serde_json::json!({}));
    assert_eq!(session["background"][1], 1.0, "{session}");
    let presets = exec(s, "brush.presets.list", serde_json::json!({}));
    let name = presets["presets"][0]["name"].as_str().expect("there are brush presets").to_string();
    exec(s, "tools.setBrush", serde_json::json!({"preset": name}));
    unsafe { loki_session_free(s) };
}

/// The options the tools carry themselves, the Layers panel's blend and opacity, and a tap in History.
#[test]
fn tool_options_layer_controls_and_history_reach_the_engine() {
    let s = loki_session_new();
    exec(s, "tools.setColors", serde_json::json!({"foreground": "#0000ff"}));
    exec(s, "paint.bucket", serde_json::json!({"x": 5, "y": 5, "tolerance": 10, "contiguous": false}));
    assert!(pixel(s, 50.0, 50.0)[2] > 0.9);
    exec(s, "select.magicWand", serde_json::json!({"x": 5, "y": 5, "tolerance": 10, "contiguous": true, "mode": "replace"}));
    exec(s, "select.deselect", serde_json::json!({}));
    exec(s, "paint.gradient", serde_json::json!({"from": [0, 0], "to": [100, 100], "style": "radial"}));
    exec(s, "shape.create", serde_json::json!({"kind": "polygon", "rect": [10, 10, 80, 80], "sides": 7}));
    exec(s, "shape.create", serde_json::json!({"kind": "line", "from": [10, 10], "to": [90, 90], "weight": 6}));
    exec(s, "type.create", serde_json::json!({"x": 20, "y": 60, "text": "Hi", "size": 36, "color": "#0000ff"}));
    exec(s, "layer.new.layer", serde_json::json!({}));
    let d = exec(s, "document.inspect", serde_json::json!({}));
    let top = d["layers"].as_array().unwrap().iter().find(|l| l["selected"] == true).expect("a selected layer").clone();
    let id = top["id"].as_u64().unwrap();
    exec(s, "layer.setProps", serde_json::json!({"layer": id, "blend": "Multiply"}));
    exec(s, "layer.setProps", serde_json::json!({"layer": id, "opacity": 0.4}));
    let d = exec(s, "document.inspect", serde_json::json!({}));
    let top = d["layers"].as_array().unwrap().iter().find(|l| l["id"].as_u64() == Some(id)).unwrap().clone();
    assert_eq!(top["blend"], "Multiply", "{top}");
    assert!((top["opacity"].as_f64().unwrap() - 0.4).abs() < 1e-6, "{top}");
    exec(s, "layer.arrange.sendBackward", serde_json::json!({}));
    // A tap on an earlier History row goes back to it.
    let steps = d["history"].as_array().unwrap().len();
    assert!(steps > 3, "{d}");
    exec(s, "history.selectState", serde_json::json!({"state": 1}));
    let after = exec(s, "document.inspect", serde_json::json!({}));
    assert!(after["layers"].as_array().unwrap().len() < d["layers"].as_array().unwrap().len(), "going back should drop the later layers");
    exec(s, "paint.magicEraser", serde_json::json!({"x": 5, "y": 5, "tolerance": 10, "contiguous": true}));
    unsafe { loki_session_free(s) };
}

/// The frame Free Transform opens on, and the quad it is sent to, as the box's handles do.
#[test]
fn the_transform_frame_is_the_layers_content_and_a_quad_moves_it() {
    let s = loki_session_new();
    assert!(call(s, "loki.transformFrame", None)["result"]["frame"].is_null() || call(s, "loki.transformFrame", None)["result"]["frame"].is_array());
    exec(s, "layer.new.layer", serde_json::json!({}));
    exec(s, "tools.setColors", serde_json::json!({"foreground": "#ff0000"}));
    exec(s, "select.rect", serde_json::json!({"x": 100, "y": 100, "width": 200, "height": 100}));
    exec(s, "edit.fill", serde_json::json!({"color": "#ff0000"}));
    exec(s, "select.deselect", serde_json::json!({}));
    let f = call(s, "loki.transformFrame", None)["result"]["frame"].clone();
    assert_eq!(f, serde_json::json!([100, 100, 300, 200]), "{f}");
    // Scale about the top-left corner by 2, as dragging the bottom-right handle out would.
    exec(s, "edit.transform", serde_json::json!({"rect": [100, 100, 300, 200], "quad": [[100, 100], [500, 100], [500, 300], [100, 300]]}));
    let f = call(s, "loki.transformFrame", None)["result"]["frame"].clone();
    assert_eq!(f, serde_json::json!([100, 100, 500, 300]), "{f}");
    unsafe { loki_session_free(s) };
}
