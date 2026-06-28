import {
  buildGatewayUserContextString,
  buildGatewayUserContextStringV2,
  buildGatewayUserContextStringV3,
  grantsAccess,
} from './verifier';

describe('buildGatewayUserContextStringV3', () => {
  it('all fields present golden string', () => {
    expect(
      buildGatewayUserContextStringV3(
        'sub-ext-id',
        'prod-uuid-1',
        ['roleA', 'roleB'],
        'ACTIVE',
        'SUBSCRIPTION',
        'PER_REQUEST',
        'request'
      )
    ).toBe('3sub-ext-idprod-uuid-1roleA,roleBACTIVESUBSCRIPTIONPER_REQUESTrequest');
  });

  it('empty roles golden string', () => {
    expect(buildGatewayUserContextStringV3('user-1', 'prod-1', [], 'TRIAL', null, null, null)).toBe(
      '3user-1prod-1TRIAL'
    );
  });

  it('billing fields absent golden string', () => {
    expect(buildGatewayUserContextStringV3('owner-id', '', [], 'ACTIVE', null, null, null)).toBe(
      '3owner-idACTIVE'
    );
  });

  it('non-access status golden string', () => {
    expect(
      buildGatewayUserContextStringV3(
        'user-x',
        'prod-x',
        ['r1'],
        'EXPIRED',
        'PREPAID',
        'PER_REQUEST',
        'request'
      )
    ).toBe('3user-xprod-xr1EXPIREDPREPAIDPER_REQUESTrequest');
  });
});

describe('buildGatewayUserContextStringV2 (compat)', () => {
  it('owner no roles', () => {
    expect(buildGatewayUserContextStringV2('user-1', 'owner', [], true, null, null, null)).toBe(
      '2user-1ownertrue'
    );
  });
});

describe('buildGatewayUserContextString v1 (compat)', () => {
  it('with quota', () => {
    expect(buildGatewayUserContextString('a', 'b', ['x'], 5, false, 'S', 'M', 'U')).toBe('abx5falseSMU');
  });
});

describe('grantsAccess', () => {
  it('returns true for invoke-eligible statuses', () => {
    expect(grantsAccess('ACTIVE')).toBe(true);
    expect(grantsAccess('trial')).toBe(true);
    expect(grantsAccess('CANCELLING')).toBe(true);
    expect(grantsAccess('CANCELLING_PENDING')).toBe(true);
  });

  it('returns false for other or missing status', () => {
    expect(grantsAccess('EXPIRED')).toBe(false);
    expect(grantsAccess(null)).toBe(false);
    expect(grantsAccess('')).toBe(false);
  });
});
