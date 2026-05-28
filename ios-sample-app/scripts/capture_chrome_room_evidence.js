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
  MEETING_ID_VALUE = '',
  DISPLAY_NAME_VALUE = '',
  DEBUG_FIELDS = '0',
  REQUIRE_PRODUCE_TAGS = '',
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
const meetingIdValue = String(MEETING_ID_VALUE).trim();
const displayNameValue = String(DISPLAY_NAME_VALUE).trim();
const debugFields = ['1', 'true', 'yes', 'on'].includes(String(DEBUG_FIELDS).trim().toLowerCase());
const requiredProduceTags = String(REQUIRE_PRODUCE_TAGS)
  .split(',')
  .map(value => value.trim().toLowerCase())
  .filter(Boolean);
const produceStartDelayMs = Number.parseInt(PRODUCE_START_DELAY_MS, 10);
const produceRetryAfterMs = Number.parseInt(PRODUCE_RETRY_AFTER_MS, 10);

if (!Number.isFinite(minActiveVideos) || minActiveVideos < 0) {
  console.error(`Invalid MIN_ACTIVE_VIDEOS: ${MIN_ACTIVE_VIDEOS}`);
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

if (!Number.isFinite(produceRetryAfterMs) || produceRetryAfterMs < 0) {
  console.error(`Invalid PRODUCE_RETRY_AFTER_MS: ${PRODUCE_RETRY_AFTER_MS}`);
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

  const producerTagsReady = () => {
    if (requiredProduceTags.length === 0) return true;
    const summary = summarizeProducerEvidence();
    return requiredProduceTags.every(tag => (summary.ackCounts[tag] || 0) > 0);
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
        const clickSettleMs = ${clickSettleMs};
        const clickRepeatMs = ${clickRepeatMs};
        const waitForText = ${JSON.stringify(waitForText)};
        const meetingIdValue = ${JSON.stringify(meetingIdValue)};
        const displayNameValue = ${JSON.stringify(displayNameValue)};
        const debugFields = ${debugFields ? 'true' : 'false'};
        const missingProduceTags = ${JSON.stringify(missingProduceTags)};
        const produceStartDelayMs = ${produceStartDelayMs};
        const produceRetryAfterMs = ${produceRetryAfterMs};
        const now = Date.now();
        window.__mediasfuProbeState = window.__mediasfuProbeState || {
          startedAt: now,
          lastLabelClickAt: 0,
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

        if (shouldClick && productionReady) {
          const clickFirstAvailable = labels => {
            const button = labels.map(findButton).find(Boolean);
            if (button) {
              button.click();
              return true;
            }
            return false;
          };

          if (missingProduceTags.includes('audio')) {
            clickFirstAvailable(['Unmute']);
          } else if (missingProduceTags.includes('video')) {
            clickFirstAvailable(['Video On']);
          } else {
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

        const bodyText = document.body.innerText.slice(0, 1000);
        const inRoom = /People|End|Share Screen|Video Off|Mute/.test(bodyText)
          && !/Enter your Meeting ID to continue/.test(bodyText);

        const allVideos = Array.from(document.querySelectorAll('video')).map(video => ({
          paused: video.paused,
          readyState: video.readyState,
          width: video.videoWidth,
          height: video.videoHeight,
          srcObject: Boolean(video.srcObject)
        }));

        const activeVideos = allVideos.filter(video =>
          video.readyState >= 2 && video.width > 0 && video.height > 0 && video.srcObject
        );

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

        return {
          url: location.href,
          title: document.title,
          bodyText,
          inRoom,
          textReady: !waitForText || bodyText.includes(waitForText),
          activeVideoCount: activeVideos.length,
          allVideos,
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

    if (snapshot && snapshot.activeVideoCount >= minActiveVideos && snapshot.textReady !== false && producerTagsReady()) {
      success = true;
      break;
    }

    await delay(1000);
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
    timeoutSeconds,
    requiredProduceTags,
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