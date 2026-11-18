export function formatBdt(amount: number): string {
  const isNegative = amount < 0;
  const absAmount = Math.abs(amount);
  const intPart = Math.floor(absAmount);
  const decimalPart = Math.round((absAmount - intPart) * 100);

  const formattedInt = formatSouthAsian(intPart);
  const result = decimalPart > 0 ? `${formattedInt}.${decimalPart.toString().padStart(2, '0')}` : formattedInt;

  return isNegative ? `-৳${result}` : `৳${result}`;
}

function formatSouthAsian(n: number): string {
  const s = n.toString();
  if (s.length <= 3) return s;
  const last3 = s.substring(s.length - 3);
  let remaining = s.substring(0, s.length - 3);
  const parts: string[] = [];
  while (remaining.length > 2) {
    parts.unshift(remaining.substring(remaining.length - 2));
    remaining = remaining.substring(0, remaining.length - 2);
  }
  if (remaining.length > 0) {
    parts.unshift(remaining);
  }
  return parts.join(',') + ',' + last3;
}
