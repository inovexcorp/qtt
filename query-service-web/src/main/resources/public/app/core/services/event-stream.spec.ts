import { EventStreamParser, readEventStream, StreamError, StreamEvent } from './event-stream';

describe('EventStreamParser', () => {
  it('reads named events, with or without a space after the colon (Quarkus writes none)', () => {
    const parser = new EventStreamParser();
    expect(parser.push('event: datasources\ndata: {"a":1}\n\nevent:datasources\ndata:{"a":2}\n\n')).toEqual([
      { event: 'datasources', data: '{"a":1}' },
      { event: 'datasources', data: '{"a":2}' },
    ]);
  });

  it('joins events split across chunks anywhere, including inside \\r\\n', () => {
    const parser = new EventStreamParser();
    const out: StreamEvent[] = [];
    for (const chunk of ['eve', 'nt: x\r', '\ndata: hel', 'lo\r\n', '\r\n']) {
      out.push(...parser.push(chunk));
    }
    expect(out).toEqual([{ event: 'x', data: 'hello' }]);
  });

  it('joins multi-line data, skips comments (keep-alives), defaults the name to message', () => {
    const parser = new EventStreamParser();
    expect(parser.push(': keep-alive\n\ndata: one\ndata: two\n\n')).toEqual([{ event: 'message', data: 'one\ntwo' }]);
  });

  it('ignores an event with no data', () => {
    expect(new EventStreamParser().push('event: x\n\n')).toEqual([]);
  });
});

describe('readEventStream', () => {
  const body = (...chunks: string[]) => new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach(c => controller.enqueue(new TextEncoder().encode(c)));
      controller.close();
    },
  });

  it('sends X-Requested-With and delivers events until the stream ends', async () => {
    const fetchFn = vi.fn().mockResolvedValue(new Response(body('event: a\ndata: 1\n\n', 'event: b\ndata: 2\n\n')));
    const events: StreamEvent[] = [];
    const onOpen = vi.fn();
    await readEventStream(fetchFn, '/s', new AbortController().signal, { onOpen, onEvent: e => events.push(e) });

    expect(fetchFn.mock.calls[0][1].headers['X-Requested-With']).toBe('JavaScript');
    expect(onOpen).toHaveBeenCalledTimes(1);
    expect(events.map(e => e.event)).toEqual(['a', 'b']);
  });

  it('rejects with the status when the server refuses the stream', async () => {
    const fetchFn = vi.fn().mockResolvedValue(new Response('', { status: 499 }));
    await expect(readEventStream(fetchFn, '/s', new AbortController().signal, { onOpen: vi.fn(), onEvent: vi.fn() }))
      .rejects.toEqual(new StreamError(499));
  });

  it('rejects with status 0 on a network error', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(readEventStream(fetchFn, '/s', new AbortController().signal, { onOpen: vi.fn(), onEvent: vi.fn() }))
      .rejects.toMatchObject({ status: 0 });
  });
});
