"use strict";
(function () {
  function native(op, extra) {
    const payload = Object.assign({ op: op }, extra || {});
    try {
      return JSON.parse(GalaxyNative.command(JSON.stringify(payload)) || "{}");
    } catch (error) {
      return { error: error.message || "Playback failed." };
    }
  }

  function GalaxyAudio() {
    this._time = 0;
    this._duration = NaN;
    this._src = "";
    this._rate = 1;
    this._volume = 0.8;
    this._fromNative = false;
    this.paused = true;
    this.ended = false;
    this.autoplay = false;
    this.currentSrc = "";
    this.readyState = 0;
    this.dataset = { autoplay: "" };
    this._events = {};
  }

  GalaxyAudio.prototype.addEventListener = function (type, handler) {
    (this._events[type] || (this._events[type] = [])).push(handler);
  };
  GalaxyAudio.prototype.removeEventListener = function (type, handler) {
    this._events[type] = (this._events[type] || []).filter(item => item !== handler);
  };
  GalaxyAudio.prototype.dispatchEvent = function (event) {
    (this._events[event.type] || []).forEach(handler => handler.call(this, event));
    return true;
  };
  GalaxyAudio.prototype.play = function () {
    const result = native("play", { url: this._src, position: this._time, rate: this._rate, volume: this._volume });
    if (result.error) {
      const error = new Error(result.error);
      error.name = "NotAllowedError";
      return Promise.reject(error);
    }
    this.paused = false;
    this.ended = false;
    return Promise.resolve();
  };
  GalaxyAudio.prototype.pause = function () { native("pause"); };
  GalaxyAudio.prototype.load = function () {};
  GalaxyAudio.prototype.fastSeek = function (value) { this.currentTime = value; };
  GalaxyAudio.prototype.removeAttribute = function (name) {
    if (name === "src") {
      this._src = "";
      this.currentSrc = "";
      this._duration = NaN;
      native("stop");
    }
  };
  Object.defineProperty(GalaxyAudio.prototype, "src", {
    get: function () { return this._src; },
    set: function (value) {
      const next = String(value || "");
      if (next && next === this._src && this.currentSrc === next) {
        const same = native("prepare", { url: next });
        if (same.ready) this._applyReady(same);
        return;
      }
      this._src = next;
      this.currentSrc = next;
      this.ended = false;
      this._duration = NaN;
      if (!next) return;
      const prepared = native("prepare", { url: next });
      if (prepared.ready) this._applyReady(prepared);
    }
  });
  Object.defineProperty(GalaxyAudio.prototype, "currentTime", {
    get: function () { return this._time || 0; },
    set: function (value) {
      this._time = Number(value) || 0;
      if (!this._fromNative) native("seek", { position: this._time });
    }
  });
  Object.defineProperty(GalaxyAudio.prototype, "duration", {
    get: function () { return this._duration; }
  });
  Object.defineProperty(GalaxyAudio.prototype, "playbackRate", {
    get: function () { return this._rate; },
    set: function (value) {
      this._rate = Number(value) || 1;
      native("rate", { rate: this._rate });
    }
  });
  Object.defineProperty(GalaxyAudio.prototype, "volume", {
    get: function () { return this._volume; },
    set: function (value) {
      this._volume = Math.max(0, Math.min(1, Number(value) || 0));
      native("volume", { volume: this._volume });
    }
  });
  GalaxyAudio.prototype._applyReady = function (payload) {
    this._fromNative = true;
    this._duration = Number(payload.duration);
    this._time = Number(payload.position) || 0;
    this.paused = !payload.playing;
    this._fromNative = false;
    this.readyState = 1;
    this.dispatchEvent(new Event("loadedmetadata"));
  };
  GalaxyAudio.prototype.onNative = function (payload) {
    if (!payload || !payload.type) return;
    if (payload.type === "time") {
      this._fromNative = true;
      this._time = Number(payload.position) || 0;
      if (isFinite(payload.duration)) this._duration = Number(payload.duration);
      this.paused = Boolean(payload.paused);
      this.ended = Boolean(payload.ended);
      this._fromNative = false;
      this.dispatchEvent(new Event("timeupdate"));
      return;
    }
    if (payload.type === "loadedmetadata") {
      this._applyReady(payload);
      return;
    }
    if (payload.type === "play") {
      this.paused = false;
      this.ended = false;
      this.dispatchEvent(new Event("play"));
      return;
    }
    if (payload.type === "pause") {
      this.paused = true;
      this.dispatchEvent(new Event("pause"));
      return;
    }
    if (payload.type === "ended") {
      this._fromNative = true;
      if (isFinite(this._duration)) this._time = this._duration;
      this.paused = true;
      this.ended = true;
      this._fromNative = false;
      this.dispatchEvent(new Event("ended"));
      return;
    }
    if (payload.type === "error") this.dispatchEvent(new Event("error"));
  };

  window.galaxyAudio = new GalaxyAudio();
  window.GalaxyNative = window.GalaxyNative || null;

  document.getElementById("galaxy-settings").addEventListener("click", function () {
    GalaxyNative.openSettings();
  });
})();
