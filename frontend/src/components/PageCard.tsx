import { Card as AntCard } from 'antd';
import type { CardProps } from 'antd';

interface PageCardProps extends CardProps {
  children: React.ReactNode;
}

/**
 * Styled card component for page sections
 */
export const PageCard: React.FC<PageCardProps> = ({ children, ...props }) => {
  return (
    <AntCard
      bordered={false}
      style={{
        boxShadow: '0 1px 2px rgba(0, 0, 0, 0.03)',
        marginBottom: 16,
        ...props.style,
      }}
      {...props}
    >
      {children}
    </AntCard>
  );
};
