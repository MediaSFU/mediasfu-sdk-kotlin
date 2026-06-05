#!/usr/bin/env node

const fs = require('fs');

const {
  CHROME_DEBUG_PORT,
  ROOM_NAME,
  SHOT_PATH,
  JSON_PATH,
  MIN_ACTIVE_VIDEOS = '2',
  TIMEOUT_SECONDS = '90',
  AUTO_CLICK = '1',
  CLICK_LABELS = '',
  CLICK_SETTLE_MS = '0',
  CLICK_REPEAT_MS = '5000',
  WAIT_FOR_TEXT = '',
  POST_READY_DELAY_MS = '0',
  MEETING_ID_VALUE = '',
  DISPLAY_NAME_VALUE = '',
  DEBUG_FIELDS = '0',
  REQUIRE_PRODUCE_TAGS = '',
  REQUIRE_REMOTE_VIDEO = '0',
  MIN_REMOTE_VIDEOS = '1',
  REMOTE_VIDEO_MUST_BE_NONBLANK = '1',
  ALLOW_DOM_ACTIVE_TAG_FALLBACK = '0',
  KEEP_ALIVE_CLICK_LABELS = "I'm Here,I’m Here",
  KEEP_ALIVE_CLICK_REPEAT_MS = '15000',
  // CRITICAL: keep a small post-join delay before auto-producing media.
  // The web refs behave more reliably when the room and transports settle first.
  PRODUCE_START_DELAY_MS = '2000',
  PRODUCE_RETRY_AFTER_MS = '12000',
} = process.env;

if (!CHROME_DEBUG_PORT || !ROOM_NAME || !SHOT_PATH || !JSON_PATH) {
  console.error('Missing required env: CHROME_DEBUG_PORT, ROOM_NAME, SHOT_PATH, JSON_PATH');
  process.exit(1);
}

const minActiveVideos = Number.parseInt(MIN_ACTIVE_VIDEOS, 10);
const timeoutSeconds = Number.parseInt(TIMEOUT_SECONDS, 10);
const clickSettleMs = Number.parseInt(CLICK_SETTLE_MS, 10);
const clickRepeatMs = Number.parseInt(CLICK_REPEAT_MS, 10);
const clickLabels = String(CLICK_LABELS)
  .split(',')
  .map(value => value.trim())
  .filter(Boolean);
const waitForText = String(WAIT_FOR_TEXT).trim();
const postReadyDelayMs = Number.parseInt(POST_READY_DELAY_MS, 10);
const meetingIdValue = String(MEETING_ID_VALUE).trim();
const displayNameValue = String(DISPLAY_NAME_VALUE).trim();
const debugFields = ['1', 'true', 'yes', 'on'].includes(String(DEBUG_FIELDS).trim().toLowerCase());
const requireRemoteVideo = ['1', 'true', 'yes', 'on'].includes(String(REQUIRE_REMOTE_VIDEO).trim().toLowerCase());
const minRemoteVideos = Number.parseInt(MIN_REMOTE_VIDEOS, 10);
const remoteVideoMustBeNonBlank = ['1', 'true', 'yes', 'on'].includes(
  String(REMOTE_VIDEO_MUST_BE_NONBLANK).trim().toLowerCase()
);
const requiredProduceTags = String(REQUIRE_PRODUCE_TAGS)
  .split(',')
  .map(value => value.trim().toLowerCase())
  .filter(Boolean);
const keepAliveClickLabels = String(KEEP_ALIVE_CLICK_LABELS)
  .split(',')
  .map(value => value.trim())
  .filter(Boolean);
const allowDomActiveTagFallback = ['1', 'true', 'yes', 'on'].includes(
  String(ALLOW_DOM_ACTIVE_TAG_FALLBACK).trim().toLowerCase()
);
const keepAliveClickRepeatMs = Number.parseInt(KEEP_ALIVE_CLICK_REPEAT_MS, 10);
const produceStartDelayMs = Number.parseInt(PRODUCE_START_DELAY_MS, 10);
const produceRetryAfterMs = Number.parseInt(PRODUCE_RETRY_AFTER_MS, 10);

if (!Number.isFinite(minActiveVideos) || minActiveVideos < 0) {
  console.error(`Invalid MIN_ACTIVE_VIDEOS: ${MIN_ACTIVE_VIDEOS}`);
  process.exit(1);
}

if (!Number.isFinite(minRemoteVideos) || minRemoteVideos < 0) {
  console.error(`Invalid MIN_REMOTE_VIDEOS: ${MIN_REMOTE_VIDEOS}`);
  process.exit(1);
}

if (!Number.isFinite(timeoutSeconds) || timeoutSeconds < 1) {
  console.error(`Invalid TIMEOUT_SECONDS: ${TIMEOUT_SECONDS}`);
  process.exit(1);
}

if (!Number.isFinite(clickSettleMs) || clickSettleMs < 0) {
  console.error(`Invalid CLICK_SETTLE_MS: ${CLICK_SETTLE_MS}`);
  process.exit(1);
}

if (!Number.isFinite(clickRepeatMs) || clickRepeatMs < 0) {
  console.error(`Invalid CLICK_REPEAT_MS: ${CLICK_REPEAT_MS}`);
  process.exit(1);
}

if (!Number.isFinite(postReadyDelayMs) || postReadyDelayMs < 0) {
  console.error(`Invalid POST_READY_DELAY_MS: ${POST_READY_DELAY_MS}`);
  process.exit(1);
}

if (!Number.isFinite(produceRetryAfterMs) || produceRetryAfterMs < 0) {
  console.error(`Invalid PRODUCE_RETRY_AFTER_MS: ${PRODUCE_RETRY_AFTER_MS}`);
  process.exit(1);
}

if (!Number.isFinite(keepAliveClickRepeatMs) || keepAliveClickRepeatMs < 0) {
  console.error(`Invalid KEEP_ALIVE_CLICK_REPEAT_MS: ${KEEP_ALIVE_CLICK_REPEAT_MS}`);
  process.exit(1);
}

if (!Number.isFinite(produceStartDelayMs) || produceStartDelayMs < 0) {
  console.error(`Invalid PRODUCE_START_DELAY_MS: ${PRODUCE_START_DELAY_MS}`);
  process.exit(1);
}

const autoClick = ['1', 'true', 'yes', 'on'].includes(String(AUTO_CLICK).trim().toLowerCase());

const delay = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));

async function evaluateValue(send, expression) {
  const result = await send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
  });
  return result?.result?.result?.value;
}

async function findTab() {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    try {
      const tabs = await fetch(`http://127.0.0.1:${CHROME_DEBUG_PORT}/json/list`).then(response => response.json());
      const tab = tabs.find(candidate => (candidate.url || '').includes(`/meet/${ROOM_NAME}`));
      if (tab) {
        return tab;
      }
    } catch (_) {
    }

    await delay(300);
  }

  throw new Error(`No Chrome tab found for room ${ROOM_NAME}`);
}

async function driveJoinFlow(send) {
  if (!meetingIdValue && !displayNameValue) {
    return;
  }

  for (let attempt = 0; attempt < 30; attempt += 1) {
    const state = await evaluateValue(send, `(() => {
      const visible = element => {
        if (!element) return false;
        const rect = element.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
      };
      const bodyText = document.body.innerText.slice(0, 1000);
      const displayNameField = document.querySelector('#userNameInputMain')
        || document.querySelector('input[placeholder*="Your name"]');
      return {
        inRoom: /People|End|Share Screen|Video Off|Mute/.test(bodyText)
          && !/Enter your Meeting ID to continue/.test(bodyText),
        meetingIdVisible: Boolean(document.querySelector('#meetingIDInput') && visible(document.querySelector('#meetingIDInput'))),
        displayNameVisible: Boolean(displayNameField && visible(displayNameField)),
      };
    })()`);

    if (state?.inRoom) {
      return;
    }

    if (state?.meetingIdVisible && meetingIdValue) {
      await evaluateValue(send, `(() => {
        const input = document.querySelector('#meetingIDInput');
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
        if (input && setter) {
          setter.call(input, ${JSON.stringify(meetingIdValue)});
          input.focus();
          input.dispatchEvent(new InputEvent('input', {
            bubbles: true,
            inputType: 'insertText',
            data: ${JSON.stringify(meetingIdValue)},
          }));
          input.dispatchEvent(new Event('change', { bubbles: true }));
        }
        const button = Array.from(document.querySelectorAll('button')).find(candidate =>
          (candidate.textContent || '').replace(/\s+/g, ' ').trim() === 'Continue'
        );
        if (button) {
          button.click();
        }
      })()`);
      await delay(1500);
      continue;
    }

    if (state?.displayNameVisible && displayNameValue) {
      await evaluateValue(send, `(() => {
        const input = document.querySelector('#userNameInputMain')
          || document.querySelector('input[placeholder*="Your name"]');
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
        if (input && setter) {
          setter.call(input, ${JSON.stringify(displayNameValue)});
          input.focus();
          input.dispatchEvent(new InputEvent('input', {
            bubbles: true,
            inputType: 'insertText',
            data: ${JSON.stringify(displayNameValue)},
          }));
          input.dispatchEvent(new Event('change', { bubbles: true }));
          input.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, key: 'Enter' }));
          input.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: 'Enter' }));
        }
        const joinButtons = Array.from(document.querySelectorAll('button')).filter(candidate =>
          (candidate.textContent || '').replace(/\s+/g, ' ').trim() === 'Join Meeting'
        );
        const button = joinButtons[joinButtons.length - 1];
        if (button) {
          button.click();
        }
      })()`);
      await delay(2000);
      continue;
    }

    await delay(1000);
  }
}

async function main() {
  const tab = await findTab();
  const ws = new WebSocket(tab.webSocketDebuggerUrl);
  let nextId = 1;
  const pending = new Map();
  const pendingProduceAcks = new Map();
  const producerEvidence = {
    sent: [],
    acks: [],
    console: [],
    websocketErrors: [],
  };

  const classifyProduceTag = payload => {
    const kind = String(payload?.kind || '').toLowerCase();
    const mediaTag = String(payload?.appData?.mediaTag || payload?.appData?.source || '').toLowerCase();
    if (mediaTag.includes('screen')) return 'screen';
    if (kind === 'audio') return 'audio';
    if (kind === 'video') return 'video';
    return kind || 'unknown';
  };

  const trimEvidence = () => {
    producerEvidence.sent = producerEvidence.sent.slice(-30);
    producerEvidence.acks = producerEvidence.acks.slice(-30);
    producerEvidence.console = producerEvidence.console.slice(-40);
    producerEvidence.websocketErrors = producerEvidence.websocketErrors.slice(-20);
  };

  const parseSocketIoJson = value => {
    try {
      return JSON.parse(value);
    } catch (_) {
      return null;
    }
  };

  const recordSocketFrame = (direction, payloadData) => {
    if (typeof payloadData !== 'string') return;

    const produceIndex = payloadData.indexOf('["transport-produce"');
    if (direction === 'sent' && produceIndex >= 0) {
      const prefix = payloadData.slice(0, produceIndex);
      const ackId = prefix.match(/(\d+)$/)?.[1] || '';
      const packet = parseSocketIoJson(payloadData.slice(produceIndex));
      const eventPayload = Array.isArray(packet) ? packet[1] : null;
      const tag = classifyProduceTag(eventPayload);
      const entry = {
        at: new Date().toISOString(),
        ackId,
        tag,
        kind: eventPayload?.kind || '',
        mediaTag: eventPayload?.appData?.mediaTag || '',
        hasRtpParameters: Boolean(eventPayload?.rtpParameters),
      };
      producerEvidence.sent.push(entry);
      if (ackId) pendingProduceAcks.set(ackId, entry);
      trimEvidence();
      return;
    }

    if (direction === 'received') {
      const ackMatch = payloadData.match(/^43(?:\/[^,]+,)?(\d+)(\[.*)$/);
      if (ackMatch && pendingProduceAcks.has(ackMatch[1])) {
        const original = pendingProduceAcks.get(ackMatch[1]);
        pendingProduceAcks.delete(ackMatch[1]);
        const packet = parseSocketIoJson(ackMatch[2]);
        const first = Array.isArray(packet) ? packet[0] : null;
        producerEvidence.acks.push({
          at: new Date().toISOString(),
          ackId: ackMatch[1],
          tag: original.tag,
          kind: original.kind,
          mediaTag: original.mediaTag,
          producerId: first?.id || first?.producerId || '',
          payloadKeys: first && typeof first === 'object' ? Object.keys(first).slice(0, 8) : [],
        });
        trimEvidence();
      }
    }
  };

  const summarizeProducerEvidence = () => {
    const sentCounts = {};
    const ackCounts = {};
    for (const entry of producerEvidence.sent) {
      sentCounts[entry.tag] = (sentCounts[entry.tag] || 0) + 1;
    }
    for (const entry of producerEvidence.acks) {
      ackCounts[entry.tag] = (ackCounts[entry.tag] || 0) + 1;
    }
    return {
      requiredProduceTags,
      sentCounts,
      ackCounts,
      pendingAckIds: Array.from(pendingProduceAcks.keys()).slice(-10),
      sent: producerEvidence.sent,
      acks: producerEvidence.acks,
      console: producerEvidence.console,
      websocketErrors: producerEvidence.websocketErrors,
    };
  };

  const producerTagsReady = (domActiveTags = []) => {
    if (requiredProduceTags.length === 0) return true;
    const summary = summarizeProducerEvidence();
    return requiredProduceTags.every(tag =>
      (summary.ackCounts[tag] || 0) > 0 || (allowDomActiveTagFallback && domActiveTags.includes(tag))
    );
  };

  ws.onmessage = event => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
      pending.get(message.id)(message);
      pending.delete(message.id);
      return;
    }

    if (message.method === 'Network.webSocketFrameSent') {
      recordSocketFrame('sent', message.params?.response?.payloadData || '');
    } else if (message.method === 'Network.webSocketFrameReceived') {
      recordSocketFrame('received', message.params?.response?.payloadData || '');
    } else if (message.method === 'Runtime.consoleAPICalled') {
      producerEvidence.console.push({
        at: new Date().toISOString(),
        type: message.params?.type || '',
        text: (message.params?.args || []).map(arg => String(arg.value ?? arg.description ?? '')).join(' ').slice(0, 500),
      });
      trimEvidence();
    } else if (message.method === 'Network.webSocketFrameError') {
      producerEvidence.websocketErrors.push({
        at: new Date().toISOString(),
        errorMessage: message.params?.errorMessage || '',
      });
      trimEvidence();
    }
  };

  await new Promise((resolve, reject) => {
    ws.onopen = resolve;
    ws.onerror = reject;
  });

  const send = (method, params = {}) => new Promise(resolve => {
    const id = nextId;
    nextId += 1;
    pending.set(id, resolve);
    ws.send(JSON.stringify({ id, method, params }));
  });

  await send('Page.enable');
  await send('Runtime.enable');
  await send('Network.enable');
  await driveJoinFlow(send);

  let snapshot = null;
  let success = false;

  for (let attempt = 0; attempt < timeoutSeconds; attempt += 1) {
    const producerSummary = summarizeProducerEvidence();
    const missingProduceTags = requiredProduceTags.filter(tag => (producerSummary.ackCounts[tag] || 0) === 0);
    const result = await send('Runtime.evaluate', {
      expression: `(() => {
        const shouldClick = ${autoClick ? 'true' : 'false'};
        const clickLabels = ${JSON.stringify(clickLabels)};
        const keepAliveClickLabels = ${JSON.stringify(keepAliveClickLabels)};
        const keepAliveClickRepeatMs = ${keepAliveClickRepeatMs};
        const clickSettleMs = ${clickSettleMs};
        const clickRepeatMs = ${clickRepeatMs};
        const waitForText = ${JSON.stringify(waitForText)};
        const meetingIdValue = ${JSON.stringify(meetingIdValue)};
        const displayNameValue = ${JSON.stringify(displayNameValue)};
        const debugFields = ${debugFields ? 'true' : 'false'};
        const missingProduceTags = ${JSON.stringify(missingProduceTags)};
        const localDisplayName = ${JSON.stringify(displayNameValue.toLowerCase())};
        const produceStartDelayMs = ${produceStartDelayMs};
        const produceRetryAfterMs = ${produceRetryAfterMs};
        const now = Date.now();
        window.__mediasfuProbeState = window.__mediasfuProbeState || {
          startedAt: now,
          lastLabelClickAt: 0,
          lastKeepAliveClickAt: 0,
          lastMeetingSubmitAt: 0,
          lastDisplayNameSubmitAt: 0,
          lastProducerResetByTag: {},
        };
        const probeState = window.__mediasfuProbeState;
        const normalizeText = value => (value || '').replace(/\s+/g, ' ').trim();
        const describeField = element => normalizeText([
          element?.getAttribute?.('placeholder'),
          element?.getAttribute?.('aria-label'),
          element?.getAttribute?.('name'),
          element?.id,
          element?.closest?.('label')?.innerText,
          element?.parentElement?.innerText,
        ].filter(Boolean).join(' '));
        const visible = element => {
          if (!element) return false;
          const rect = element.getBoundingClientRect();
          return rect.width > 0 && rect.height > 0;
        };
        const isVisibleTextField = element => {
          if (!visible(element) || element.disabled) return false;
          if (element.tagName === 'TEXTAREA') return true;
          const type = (element.getAttribute('type') || 'text').toLowerCase();
          return type === '' || type === 'text' || type === 'search' || type === 'email' || type === 'tel' || type === 'url';
        };
        const setFieldValue = (element, value) => {
          if (!element) return;
          const prototype = element.tagName === 'TEXTAREA'
            ? window.HTMLTextAreaElement.prototype
            : window.HTMLInputElement.prototype;
          const setter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
          if (setter) {
            setter.call(element, value);
          } else {
            element.value = value;
          }
          element.focus();
          element.dispatchEvent(new InputEvent('input', {
            bubbles: true,
            inputType: 'insertText',
            data: value,
          }));
          element.dispatchEvent(new Event('change', { bubbles: true }));
          element.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, key: 'Enter' }));
          element.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: 'Enter' }));
          element.blur();
        };
        const findButtons = label => Array.from(document.querySelectorAll('button')).filter(button => {
          const text = normalizeText(button.innerText || button.textContent || '');
          return !button.disabled && visible(button) && (text === label || text.includes(label));
        });
        const findButton = label => findButtons(label)[0];
        const keepAliveButton = keepAliveClickLabels.map(findButton).find(Boolean);
        if (
          shouldClick
          && keepAliveButton
          && now - (probeState.lastKeepAliveClickAt || 0) >= keepAliveClickRepeatMs
        ) {
          keepAliveButton.click();
          probeState.lastKeepAliveClickAt = now;
        }
        const findField = regex => Array.from(document.querySelectorAll('input, textarea')).find(element => {
          return visible(element) && regex.test(describeField(element));
        });
        const visibleTextFields = Array.from(document.querySelectorAll('input, textarea')).filter(isVisibleTextField);
        const pageText = normalizeText(document.body.innerText.slice(0, 1000));
        const inRoomNow = /People|End|Share Screen|Video Off|Mute/.test(pageText)
          && !/Enter your Meeting ID to continue/.test(pageText);
        if (inRoomNow && !probeState.inRoomAt) {
          probeState.inRoomAt = now;
        }
        const inlineJoinAutomation = false;

        if (inlineJoinAutomation && meetingIdValue) {
          const meetingIdField = document.querySelector('#meetingIDInput')
            || findField(/meeting id|meeting/i)
            || (/join a meeting|meeting id/i.test(pageText) ? visibleTextFields[0] : null);
          if (meetingIdField && normalizeText(meetingIdField.value) !== meetingIdValue) {
            setFieldValue(meetingIdField, meetingIdValue);
          }
          const continueButton = findButton('Continue');
          const onMeetingIdStep = /join a meeting|enter your meeting id/i.test(pageText);
          if (
            onMeetingIdStep
            && meetingIdField
            && continueButton
            && normalizeText(meetingIdField.value) === meetingIdValue
            && now - (probeState.lastMeetingSubmitAt || 0) >= 3000
          ) {
            continueButton.click();
            probeState.lastMeetingSubmitAt = now;
          }
        }

        if (inlineJoinAutomation && displayNameValue) {
          const displayNameField = document.querySelector('#userNameInputMain')
            || findField(/display name|your name|name/i)
            || (/your details|display name|join meeting/i.test(pageText)
              ? visibleTextFields.find(element => element.id !== 'meetingIDInput') || visibleTextFields[0] || null
              : null);
          if (displayNameField && normalizeText(displayNameField.value) !== displayNameValue) {
            setFieldValue(displayNameField, displayNameValue);
          }
          const joinMeetingButtons = findButtons('Join Meeting');
          const genericJoinButtons = findButtons('Join');
          const joinButton = joinMeetingButtons[joinMeetingButtons.length - 1]
            || genericJoinButtons[genericJoinButtons.length - 1]
            || null;
          const onDisplayNameStep = /your details|display name/i.test(pageText);
          if (
            onDisplayNameStep
            && displayNameField
            && joinButton
            && normalizeText(displayNameField.value) === displayNameValue
            && now - (probeState.lastDisplayNameSubmitAt || 0) >= 3000
          ) {
            joinButton.click();
            probeState.lastDisplayNameSubmitAt = now;
          }
        }

        const elapsedMs = now - probeState.startedAt;
        const inRoomElapsedMs = probeState.inRoomAt ? now - probeState.inRoomAt : 0;
        const productionReady = Boolean(probeState.inRoomAt) && inRoomElapsedMs >= produceStartDelayMs;

        const tourContainers = Array.from(document.querySelectorAll('div, section, aside, article')).filter(element =>
          visible(element)
          && (element.innerText || '').includes("Don't show this tour again")
          && (element.innerText || '').includes("Step ")
        );
        for (const tourContainer of tourContainers) {
          const checkbox = tourContainer.querySelector('input[type="checkbox"]');
          if (checkbox && !checkbox.checked) checkbox.click();
          const closeBtn = Array.from(tourContainer.querySelectorAll('button, a, [role="button"]')).find(button =>
            (button.getAttribute('aria-label') || '').includes('Close')
            || (button.className || '').toLowerCase().includes('close')
            || ((button.innerHTML || '').includes('<svg') && !button.innerText.trim())
          );
          if (closeBtn) {
            closeBtn.click();
          }
          let currentEl = tourContainer;
          while (currentEl && currentEl.tagName && currentEl.tagName.toLowerCase() !== 'body') {
            try {
              const style = window.getComputedStyle(currentEl);
              if (style.position === 'absolute' || style.position === 'fixed') {
                currentEl.style.display = 'none';
              }
            } catch (e) {}
            currentEl = currentEl.parentElement;
          }
        }
        document.querySelectorAll('.modal-backdrop, [class*="backdrop"], [class*="overlay"]').forEach(element => {
          if (visible(element)) element.style.display = 'none';
        });

        if (shouldClick && productionReady) {
          const clickFirstAvailable = labels => {
            const button = labels.map(findButton).find(Boolean);
            if (button) {
              button.click();
              return true;
            }
            return false;
          };

          let clickedMissingControl = false;
          if (missingProduceTags.includes('audio')) {
            clickedMissingControl = clickFirstAvailable(['Unmute']) || clickedMissingControl;
          }
          if (missingProduceTags.includes('video')) {
            clickedMissingControl = clickFirstAvailable(['Video On']) || clickedMissingControl;
          }
          if (!clickedMissingControl) {
            for (const label of ['Unmute', 'Video On']) {
              const button = findButton(label);
              if (button) {
                button.click();
              }
            }
          }
        }

        const resetMissingProducer = (tag, offLabels, onLabels) => {
          if (!missingProduceTags.includes(tag) || produceRetryAfterMs === 0) return false;
          if (now - probeState.startedAt < produceRetryAfterMs) return false;
          if (now - (probeState.lastProducerResetByTag[tag] || 0) < produceRetryAfterMs) return false;

          const offButton = offLabels.map(findButton).find(Boolean);
          const onButton = onLabels.map(findButton).find(Boolean);
          const button = offButton || onButton;
          if (!button) return false;

          button.click();
          probeState.lastProducerResetByTag[tag] = now;
          return true;
        };

        if (missingProduceTags.includes('audio')) {
          resetMissingProducer('audio', ['Mute'], ['Unmute']);
        } else if (missingProduceTags.includes('video')) {
          resetMissingProducer('video', ['Video Off'], ['Video On']);
        }
        resetMissingProducer('screen', ['Stop Share', 'Stop sharing'], clickLabels);

        const canClickShare = productionReady && elapsedMs >= clickSettleMs && now - probeState.lastLabelClickAt >= clickRepeatMs;
        if (canClickShare) {
          for (const label of clickLabels) {
            const button = findButton(label);
            if (button) {
              button.click();
              probeState.lastLabelClickAt = now;
              break;
            }
          }
        }

        const buttonTexts = Array.from(document.querySelectorAll('button'))
          .filter(visible)
          .map(button => normalizeText(button.innerText || button.textContent || ''))
          .filter(Boolean);
        const bodyText = document.body.innerText.slice(0, 1000);
        const inRoom = /People|End|Share Screen|Video Off|Mute/.test(bodyText)
          && !/Enter your Meeting ID to continue/.test(bodyText);

        const videoContextText = video => {
          const chunks = [];
          let element = video;
          for (let depth = 0; element && depth < 5; depth += 1) {
            const text = normalizeText(element.innerText || element.textContent || '');
            if (text) chunks.push(text);
            const aria = normalizeText(element.getAttribute?.('aria-label') || element.getAttribute?.('title') || '');
            if (aria) chunks.push(aria);
            element = element.parentElement;
          }
          return normalizeText(chunks.join(' ')).slice(0, 300);
        };

        const sampleVideoPixels = video => {
          if (video.readyState < 2 || video.videoWidth <= 0 || video.videoHeight <= 0) {
            return { sampled: false, nonblank: false, reason: 'not-ready' };
          }

          try {
            const canvas = document.createElement('canvas');
            const sampleWidth = 12;
            const sampleHeight = 12;
            canvas.width = sampleWidth;
            canvas.height = sampleHeight;
            const context = canvas.getContext('2d');
            context.drawImage(video, 0, 0, sampleWidth, sampleHeight);
            const data = context.getImageData(0, 0, sampleWidth, sampleHeight).data;
            let alphaPixels = 0;
            let lumaSum = 0;
            let lumaSquaredSum = 0;
            let minLuma = 255;
            let maxLuma = 0;

            for (let index = 0; index < data.length; index += 4) {
              const alpha = data[index + 3];
              if (alpha <= 8) continue;
              alphaPixels += 1;
              const luma = (data[index] * 0.2126) + (data[index + 1] * 0.7152) + (data[index + 2] * 0.0722);
              lumaSum += luma;
              lumaSquaredSum += luma * luma;
              minLuma = Math.min(minLuma, luma);
              maxLuma = Math.max(maxLuma, luma);
            }

            const pixelCount = sampleWidth * sampleHeight;
            if (alphaPixels < Math.max(4, pixelCount / 4)) {
              return { sampled: true, nonblank: false, reason: 'transparent', alphaPixels };
            }

            const mean = lumaSum / alphaPixels;
            const variance = Math.max(0, (lumaSquaredSum / alphaPixels) - (mean * mean));
            const stdDev = Math.sqrt(variance);
            const span = maxLuma - minLuma;
            const nearlyFlat = stdDev < 2 && span < 6;
            const blankExtreme = nearlyFlat && (mean < 5 || mean > 250);
            return {
              sampled: true,
              nonblank: !blankExtreme,
              mean: Number(mean.toFixed(2)),
              stdDev: Number(stdDev.toFixed(2)),
              min: Number(minLuma.toFixed(2)),
              max: Number(maxLuma.toFixed(2)),
              alphaPixels,
              reason: blankExtreme ? 'flat-extreme' : 'ok',
            };
          } catch (error) {
            return {
              sampled: false,
              nonblank: false,
              reason: 'sample-error',
              error: String(error?.message || error).slice(0, 160),
            };
          }
        };

        const rawVideos = Array.from(document.querySelectorAll('video')).map((video, index) => {
          const rect = video.getBoundingClientRect();
          const contextText = videoContextText(video);
          const lowerContext = contextText.toLowerCase();
          const srcObject = video.srcObject;
          const videoTracks = srcObject && typeof srcObject.getVideoTracks === 'function'
            ? srcObject.getVideoTracks().map(track => ({
                id: String(track.id || '').slice(0, 80),
                label: String(track.label || '').slice(0, 80),
                enabled: track.enabled,
                muted: track.muted,
                readyState: track.readyState,
              }))
            : [];
          const active = video.readyState >= 2 && video.videoWidth > 0 && video.videoHeight > 0 && Boolean(srcObject);
          const localNameMatch = Boolean(localDisplayName && lowerContext.includes(localDisplayName));
          const localTextMatch = /\\b(local|you|me)\\b/.test(lowerContext);
          const pixelSample = sampleVideoPixels(video);
          return {
            index,
            paused: video.paused,
            muted: video.muted,
            readyState: video.readyState,
            width: video.videoWidth,
            height: video.videoHeight,
            clientWidth: Math.round(rect.width),
            clientHeight: Math.round(rect.height),
            srcObject: Boolean(srcObject),
            active,
            probablyLocal: false,
            localNameMatch,
            localTextMatch,
            localTrackMatch: false,
            contextText,
            videoTracks,
            pixelSample,
          };
        });
        const localVideoTrackIds = new Set(rawVideos
          .filter(video => video.localNameMatch || video.localTextMatch)
          .flatMap(video => video.videoTracks.map(track => track.id))
          .filter(Boolean));
        const allVideos = rawVideos.map(video => {
          const localTrackMatch = video.videoTracks.some(track => localVideoTrackIds.has(track.id));
          return {
            ...video,
            localTrackMatch,
            probablyLocal: Boolean(video.localNameMatch || video.localTextMatch || localTrackMatch),
          };
        });

        const activeVideos = allVideos.filter(video => video.active);
        const remoteVideos = activeVideos.filter(video => !video.probablyLocal);
        const renderedRemoteVideos = remoteVideos.filter(video => video.pixelSample?.nonblank);

        const debugInputs = debugFields
          ? Array.from(document.querySelectorAll('input, textarea')).map(element => ({
              tag: element.tagName,
              type: element.getAttribute('type') || '',
              id: element.id || '',
              placeholder: element.getAttribute('placeholder') || '',
              ariaLabel: element.getAttribute('aria-label') || '',
              name: element.getAttribute('name') || '',
              valueLength: (element.value || '').length,
              visible: visible(element),
              descriptor: describeField(element).slice(0, 160),
            })).slice(0, 12)
          : undefined;
        const debugButtons = debugFields
          ? Array.from(document.querySelectorAll('button')).map(button => ({
              text: normalizeText(button.innerText || button.textContent || '').slice(0, 80),
              visible: visible(button),
              disabled: button.disabled,
              className: String(button.className || '').slice(0, 80),
            })).slice(0, 20)
          : undefined;

        const domActiveTags = [];
        const keepAlivePromptVisible = keepAliveClickLabels.some(label =>
          buttonTexts.some(text => text === label || text.includes(label))
        );
        if (buttonTexts.some(text => text === 'Mute' || text.includes('Mute'))) {
          domActiveTags.push('audio');
        }
        if (buttonTexts.some(text => text === 'Video Off' || text.includes('Video Off'))) {
          domActiveTags.push('video');
        }
        if (buttonTexts.some(text => text === 'Stop Share' || text === 'Stop sharing' || text.includes('Stop Share') || text.includes('Stop sharing'))) {
          domActiveTags.push('screen');
        }

        return {
          url: location.href,
          title: document.title,
          bodyText,
          inRoom,
          textReady: !waitForText || bodyText.includes(waitForText),
          activeVideoCount: activeVideos.length,
          remoteVideoCount: remoteVideos.length,
          renderedRemoteVideoCount: renderedRemoteVideos.length,
          remoteVideos,
          allVideos,
          domActiveTags,
          keepAlivePromptVisible,
          debugInputs,
          debugButtons
        };
      })()`,
      returnByValue: true,
    });

    snapshot = result?.result?.result?.value ?? null;
    if (snapshot) {
      snapshot.producerEvidence = summarizeProducerEvidence();
    }

    if (
      snapshot &&
      snapshot.activeVideoCount >= minActiveVideos &&
      (
        !requireRemoteVideo ||
        (
          (remoteVideoMustBeNonBlank ? snapshot.renderedRemoteVideoCount : snapshot.remoteVideoCount) >= minRemoteVideos
        )
      ) &&
      snapshot.textReady !== false &&
      producerTagsReady(Array.isArray(snapshot.domActiveTags) ? snapshot.domActiveTags : [])
    ) {
      success = true;
      break;
    }

    await delay(1000);
  }

  if (success && postReadyDelayMs > 0) {
    await delay(postReadyDelayMs);
  }

  const screenshot = await send('Page.captureScreenshot', { format: 'png', fromSurface: true });
  const imageData = screenshot?.result?.data;
  if (imageData) {
    fs.writeFileSync(SHOT_PATH, Buffer.from(imageData, 'base64'));
  }

  const evidence = {
    capturedAt: new Date().toISOString(),
    room: ROOM_NAME,
    minActiveVideos,
    requireRemoteVideo,
    minRemoteVideos,
    remoteVideoMustBeNonBlank,
    timeoutSeconds,
    requiredProduceTags,
    allowDomActiveTagFallback,
    success,
    snapshot,
  };

  fs.writeFileSync(JSON_PATH, JSON.stringify(evidence, null, 2));
  ws.close();

  console.log(JSON.stringify(evidence, null, 2));
  if (!success) {
    process.exit(2);
  }
}

main().catch(error => {
  console.error(error.stack || String(error));
  process.exit(1);
});
