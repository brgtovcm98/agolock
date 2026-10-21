export function formatPrice(amount: number | null | undefined, currency = 'KRW') {
  if (amount === null || amount === undefined) return '가격 없음'
  return `${amount.toLocaleString('ko-KR')}${currency === 'KRW' ? '원' : ` ${currency}`}`
}

export function formatDate(value: string | null | undefined) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

export function locationTypeLabel(type: string) {
  switch (type) {
    case 'ROOM':
      return '방'
    case 'SHELF':
      return '선반'
    case 'BOX':
      return '박스'
    default:
      return '위치'
  }
}
