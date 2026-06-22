import { Server, VlessConfig, VmessConfig, TrojanConfig, SshConfig, V2RayConfig } from '../types';

/**
 * Firebase server object থেকে sing-box JSON config তৈরি করে।
 * sing-box সব protocol সাপোর্ট করে: VLESS, VMess, Trojan, SSH
 */
export function buildSingboxConfig(server: Server): string {
  const outbound = buildOutbound(server);

  const config = {
    log: { level: 'error', timestamp: true },
    dns: {
      servers: [
        { tag: 'dns-remote', address: 'tls://1.1.1.1', detour: 'proxy' },
        { tag: 'dns-local', address: '223.5.5.5', detour: 'direct' },
        { tag: 'dns-block', address: 'rcode://success' }
      ],
      rules: [
        { outbound: 'any', server: 'dns-local' },
        { rule_set: 'geosite-cn', server: 'dns-local' }
      ],
      final: 'dns-remote',
      strategy: 'prefer_ipv4'
    },
    inbounds: [
      {
        type: 'tun',
        tag: 'tun-in',
        inet4_address: '172.19.0.1/30',
        mtu: 9000,
        auto_route: true,
        strict_route: false,
        endpoint_independent_nat: true,
        stack: 'system',
        sniff: true,
        sniff_override_destination: false
      }
    ],
    outbounds: [
      outbound,
      { type: 'direct', tag: 'direct' },
      { type: 'block', tag: 'block' },
      { type: 'dns', tag: 'dns-out' }
    ],
    route: {
      rules: [
        { protocol: 'dns', outbound: 'dns-out' },
        { network: 'udp', port: 443, outbound: 'block' },
        { rule_set: 'geoip-cn', outbound: 'direct' },
        { rule_set: 'geosite-cn', outbound: 'direct' }
      ],
      rule_set: [
        {
          tag: 'geoip-cn',
          type: 'remote',
          format: 'binary',
          url: 'https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-cn.srs',
          download_detour: 'direct'
        },
        {
          tag: 'geosite-cn',
          type: 'remote',
          format: 'binary',
          url: 'https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-cn.srs',
          download_detour: 'direct'
        }
      ],
      final: 'proxy',
      auto_detect_interface: true
    }
  };

  return JSON.stringify(config, null, 2);
}

function buildOutbound(server: Server): object {
  const { host, port, type, config } = server;

  if (type === 'vless') {
    const c = config as VlessConfig;
    return {
      type: 'vless',
      tag: 'proxy',
      server: host,
      server_port: port,
      uuid: c.uuid,
      flow: '',
      tls: c.tls
        ? { enabled: true, server_name: c.sni || host, insecure: false }
        : { enabled: false },
      transport: c.network === 'ws'
        ? { type: 'ws', path: c.path || '/vless', headers: { Host: c.sni || host } }
        : c.network === 'grpc'
        ? { type: 'grpc', service_name: c.path?.replace('/', '') || 'vless' }
        : undefined
    };
  }

  if (type === 'vmess') {
    const c = config as VmessConfig;
    return {
      type: 'vmess',
      tag: 'proxy',
      server: host,
      server_port: port,
      uuid: c.uuid,
      security: 'auto',
      alter_id: c.alterId || 0,
      tls: c.tls
        ? { enabled: true, server_name: c.sni || host, insecure: false }
        : { enabled: false },
      transport: c.network === 'ws'
        ? { type: 'ws', path: c.path || '/vmess', headers: { Host: c.sni || host } }
        : c.network === 'grpc'
        ? { type: 'grpc', service_name: c.path?.replace('/', '') || 'vmess' }
        : undefined
    };
  }

  if (type === 'trojan') {
    const c = config as TrojanConfig;
    return {
      type: 'trojan',
      tag: 'proxy',
      server: host,
      server_port: port,
      password: c.password,
      tls: { enabled: true, server_name: c.sni || host, insecure: false },
      transport: c.network === 'ws'
        ? { type: 'ws', path: c.path || '/trojan', headers: { Host: c.sni || host } }
        : undefined
    };
  }

  if (type === 'ssh') {
    const c = config as SshConfig;
    return {
      type: 'ssh',
      tag: 'proxy',
      server: host,
      server_port: port,
      user: c.username,
      password: c.password
    };
  }

  if (type === 'v2ray') {
    const c = config as V2RayConfig;
    const proto = c.protocol || 'vmess';
    return {
      type: proto,
      tag: 'proxy',
      server: host,
      server_port: port,
      uuid: c.uuid,
      security: proto === 'vmess' ? 'auto' : undefined,
      tls: c.tls
        ? { enabled: true, server_name: c.sni || host, insecure: false }
        : { enabled: false },
      transport: c.network === 'ws'
        ? { type: 'ws', path: c.path || '/v2ray', headers: { Host: c.sni || host } }
        : undefined
    };
  }

  // fallback — direct
  return { type: 'direct', tag: 'proxy' };
}
