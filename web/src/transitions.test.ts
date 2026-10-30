import { describe, expect, it } from 'vitest';
import { allowedTransitions } from './transitions';

describe('incident transition choices', () => {
  it('follows the backend lifecycle policy', () => {
    expect(allowedTransitions('OPEN')).toEqual(['ACKNOWLEDGED', 'MITIGATED']);
    expect(allowedTransitions('ACKNOWLEDGED')).toEqual(['MITIGATED']);
    expect(allowedTransitions('MITIGATED')).toEqual(['RESOLVED']);
    expect(allowedTransitions('RESOLVED')).toEqual([]);
  });
});
