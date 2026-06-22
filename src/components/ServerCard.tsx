import React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Server, VpnState } from '../types';

interface Props {
  server: Server;
  selected: boolean;
  vpnState: VpnState;
  onPress: () => void;
}

const TYPE_COLORS: Record<string, string> = {
  vless:  '#6366f1',
  vmess:  '#f59e0b',
  trojan: '#10b981',
  ssh:    '#3b82f6',
  v2ray:  '#8b5cf6',
};

export default function ServerCard({ server, selected, vpnState, onPress }: Props) {
  const color = TYPE_COLORS[server.type] ?? '#6b7280';
  const isConnected = selected && vpnState === 'connected';
  const isConnecting = selected && (vpnState === 'connecting' || vpnState === 'disconnecting');

  return (
    <TouchableOpacity
      activeOpacity={0.75}
      style={[styles.card, selected && styles.cardSelected, { borderColor: selected ? color : '#2a2a3e' }]}
      onPress={onPress}
    >
      <View style={styles.left}>
        <Text style={styles.flag}>{server.flag || '🌐'}</Text>
        <View style={styles.info}>
          <Text style={styles.name} numberOfLines={1}>{server.name}</Text>
          <Text style={styles.host} numberOfLines={1}>{server.host}:{server.port}</Text>
        </View>
      </View>

      <View style={styles.right}>
        <View style={[styles.badge, { backgroundColor: color + '22' }]}>
          <Text style={[styles.badgeText, { color }]}>{server.type.toUpperCase()}</Text>
        </View>

        {isConnected && (
          <View style={styles.dot} />
        )}
        {isConnecting && (
          <View style={[styles.dot, { backgroundColor: '#f59e0b' }]} />
        )}
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#16162a',
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: '#2a2a3e',
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginBottom: 10,
  },
  cardSelected: {
    backgroundColor: '#1e1e38',
  },
  left: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  flag: {
    fontSize: 28,
    marginRight: 12,
  },
  info: {
    flex: 1,
  },
  name: {
    color: '#f0f0ff',
    fontSize: 15,
    fontWeight: '600',
    marginBottom: 2,
  },
  host: {
    color: '#6b7280',
    fontSize: 12,
    fontFamily: 'monospace',
  },
  right: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  badge: {
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  badgeText: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#10b981',
  },
});
