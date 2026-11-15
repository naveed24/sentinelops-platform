import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, getServices } from './api';

afterEach(() => vi.unstubAllGlobals());

describe('error body cancellation', () => {
  it('preserves caller abort during validation response parsing', async () => {
    const controller = new AbortController();
    const abort = new DOMException('Navigation', 'AbortError');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false, status: 409,
      json: () => new Promise((_resolve, reject) => {
        controller.signal.addEventListener('abort', () => reject(abort), { once: true });
      })
    }));
    const pending = getServices('token', controller.signal);
    await Promise.resolve();
    controller.abort(abort);
    await expect(pending).rejects.toBe(abort);
  });

  it('reports a timeout during validation response parsing', async () => {
    const controller = new AbortController();
    const spy = vi.spyOn(AbortSignal, 'timeout').mockReturnValue(controller.signal);
    try {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        ok: false, status: 400,
        json: () => new Promise((_resolve, reject) => {
          controller.signal.addEventListener('abort', () => reject(controller.signal.reason), { once: true });
        })
      }));
      const pending = getServices('token');
      await Promise.resolve();
      controller.abort(new DOMException('Deadline', 'TimeoutError'));
      await expect(pending).rejects.toEqual(new ApiError(0, 'The request timed out. Please retry.'));
    } finally {
      spy.mockRestore();
    }
  });
});
