import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { inventoryApi } from '@/features/inventory/api'
import { useAddInventory } from '@/features/inventory/add-inventory-context'
import { InventoryCard } from '@/features/inventory/components/inventory-card'
import { useAuth } from '@/features/auth/auth-provider'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { SearchField } from '@/shared/ui/search-field'
import { PageHeader } from '@/shared/ui/page-header'
import { SectionTitle } from '@/shared/ui/typography'

export function HomePage() {
  const { user, space } = useAuth()
  const { openAdd } = useAddInventory()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')

  const recentQuery = useQuery({
    queryKey: ['inventory', 'home', 'recent'],
    queryFn: () => inventoryApi.inventories({ scope: 'ALL', limit: 5 }),
  })
  const recent = recentQuery.data?.items ?? []

  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col gap-4">
        <PageHeader
          title={`${user?.displayName ?? '안녕하세요'}님, 무엇을 찾으세요?`}
          description={`${space?.name ?? '내 공간'}의 물건을 이름으로 찾거나, 최근에 등록한 물건을 이어서 관리하세요.`}
        />

        <SearchField
          value={query}
          placeholder="물건 이름으로 검색"
          onChange={setQuery}
          onSubmit={(value) => {
            const next = value.trim()
            if (next) navigate(`/search?q=${encodeURIComponent(next)}`)
          }}
        />

        <section className="flex flex-col gap-3">
          <SectionTitle
            aside={
              <Link to="/inventory" className="tap underline underline-offset-2">
                전체 보기
              </Link>
            }
          >
            최근 추가
          </SectionTitle>
          {recentQuery.isLoading ? (
            <p role="status" className="text-xs">
              불러오는 중…
            </p>
          ) : recentQuery.isError ? (
            <Alert
              variant="error"
              action={
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  onClick={() => void recentQuery.refetch()}
                >
                  다시 시도
                </Button>
              }
            >
              최근 추가한 물건을 불러오지 못했습니다.
            </Alert>
          ) : recent.length === 0 ? (
            <Card>
              <CardBody className="items-start gap-2">
                <p className="text-sm font-black">아직 등록한 물건이 없어요.</p>
                <p className="text-foreground/70 text-xs">
                  지금 있는 위치를 골라 첫 물건을 등록해 보세요.
                </p>
                <Button type="button" size="sm" onClick={() => openAdd()}>
                  <Plus aria-hidden="true" className="size-4" />첫 물건 등록
                </Button>
              </CardBody>
            </Card>
          ) : (
            <div className="grid grid-cols-[minmax(0,1fr)] gap-3 md:grid-cols-2">
              {recent.map((inventory) => (
                <InventoryCard key={inventory.id} inventory={inventory} />
              ))}
            </div>
          )}
        </section>

        <section className="flex flex-col gap-3">
          <SectionTitle>바로가기</SectionTitle>
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="secondary">
              <Link to="/space">공간 둘러보기</Link>
            </Button>
            <Button asChild variant="secondary">
              <Link to="/inventory">재고 전체 보기</Link>
            </Button>
          </div>
        </section>
      </section>
    </div>
  )
}
